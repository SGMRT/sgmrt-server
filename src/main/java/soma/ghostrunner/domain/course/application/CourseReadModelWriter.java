package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.domain.RankSlot;
import soma.ghostrunner.domain.course.domain.TopRunners;
import soma.ghostrunner.domain.course.dto.query.TopRunnerRow;
import soma.ghostrunner.domain.course.exception.CourseNotFoundException;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 리드모델({@link CourseReadModel})의 <b>유일한 쓰기 진입점</b>.
 *
 * <p>계약</p>
 * <ul>
 *   <li><b>단일 진입점</b> — 리드모델을 바꾸는 모든 유즈케이스(러닝 종료·러닝 삭제/공개전환·코스명 변경·코스
 *       공개전환·코스 삭제)는 이 클래스를 통한다. 다른 계층은 리드모델을 직접 수정하지 않는다.</li>
 *   <li><b>호출자 트랜잭션 필수</b> — 모든 메서드는 {@link Propagation#MANDATORY} 라 트랜잭션 없이 호출하면
 *       예외가 난다. 리드모델 갱신은 원본(러닝/코스) 변경과 반드시 원자적으로 커밋돼야 하므로,
 *       "트랜잭션이 없어 조용히 반영되지 않는" 상황을 런타임에 막는다.</li>
 *   <li><b>X락은 이 클래스에서만</b> — 리드모델은 언제나 X락(FOR UPDATE, {@code findByCourseIdForUpdate})으로
 *       잡고 수정해 동시 갱신의 Lost Update 를 막는다. 락을 잡는 코드가 여기 밖으로 나가면 이 보장이 깨진다.</li>
 *   <li><b>리드모델이 없으면 스킵</b> — 생성 책임은 코스 공개 전환({@link #syncPublicity})에만 있다.
 *       비공개 코스처럼 리드모델이 없는 경우 나머지 메서드는 아무 일도 하지 않는다.</li>
 * </ul>
 *
 * 설계 문서: docs/refactoring/course-read-model/04-detailed-design.md §3-1
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class CourseReadModelWriter {

    private final CourseReadModelRepository readModelRepository;
    private final CourseRepository courseRepository;

    /**
     * 러닝 종료에 따른 증분 갱신. <b>저장된 러닝을 그대로 넘기면 된다.</b>
     *
     * 집계 대상 여부(공개 ∧ 일시정지 아님 ∧ 코스 소속)는 이 메서드가 스스로 판단한다 — 판정 기준이
     * 재계산({@link #recalculate})과 같은 곳에서 관리되도록 Writer 에 응집했다. 호출자에게 남는 계약은
     * "<b>저장 직후</b>에 넘길 것" 하나뿐이다(첫 러닝 판정 EXISTS 가 자기 자신을 ID 로 제외하므로
     * ID 가 채워진 영속 상태여야 한다).
     *
     * 리드모델이 없는 코스(비공개 등)는 아무 일도 하지 않는다 — 리드모델 생성은 공개 전환의 책임이다.
     * 해당 멤버의 첫 공개 러닝일 때만 러너 수를 1 증가시킨다.
     *
     * <p><b>락 보유 구간 경고</b> — 이 메서드는 러닝 종료 트랜잭션이 커밋될 때까지 코스 리드모델 행에
     * X락을 유지한다. 같은 코스를 동시에 완주한 사용자들의 러닝 저장이 그만큼 직렬화되므로,
     * <b>이 호출 이후에 무거운 작업(S3 업로드 등 외부 I/O)을 추가하지 말 것.</b>
     * (현재 {@code createRun}은 S3 업로드를 이 호출보다 앞에 두고 있다 — 그 순서를 유지해야 한다)</p>
     */
    public void applyRun(Running running) {
        if (!isAggregationTarget(running)) {
            return;
        }
        Long courseId = running.getCourse().getId();
        Long memberId = running.getMember().getId();
        int durationSeconds = running.getRunningRecord().getDuration().intValue();

        CourseReadModel readModel = readModelRepository.findByCourseIdForUpdate(courseId).orElse(null);
        if (readModel == null) {
            // 비공개 코스의 정상 스킵과 구분되지 않으므로 debug — 특정 코스만 반영이 안 될 때 레벨을 올려 확인한다.
            log.debug("Skip applyRun: read model absent. course={}, member={}", courseId, memberId);
            return;
        }

        boolean topRunnersChanged = readModel.applyRun(memberId, durationSeconds);

        boolean firstPublicRun = isMembersFirstPublicRun(courseId, memberId, running.getId());
        if (firstPublicRun) {
            readModel.updateRunnersCount(readModel.getRunnersCount() + 1);
        }

        // 대부분의 러닝은 TOP4에 못 들므로 무조건 info 를 찍으면 로그가 러닝 수만큼 늘어난다.
        // 의미 있는 상태 변화가 있을 때만 남긴다.
        if (topRunnersChanged || firstPublicRun) {
            log.info("Applied run to read model. course={}, member={}, duration={}s, top4Changed={}, firstRun={}",
                    courseId, memberId, durationSeconds, topRunnersChanged, firstPublicRun);
        }
    }

    /**
     * 리드모델 집계 대상 러닝인지 판단한다. 재계산 쿼리의 모집단(공개 ∧ 비삭제 ∧ 비일시정지)과
     * 같은 기준이어야 증분과 재계산이 어긋나지 않는다. (삭제 여부는 저장 직후 경로라 판정 불필요)
     */
    private boolean isAggregationTarget(Running running) {
        return running.isPublic()
                && !running.isHasPaused()
                && running.getCourse() != null;
    }

    /**
     * 러닝 기록을 진실의 원천으로 TOP4·러너 수를 다시 계산해 덮어쓴다. (증분 드리프트 보정)
     * 리드모델이 없는 코스는 건너뛴다.
     *
     * <p><b>락 순서</b> — 여러 코스를 재계산할 때는 반드시 {@code courseId} 오름차순으로 X락을 잡는다.
     * 서로 다른 트랜잭션이 같은 코스 집합을 다른 순서로 잠그면 InnoDB 데드락이 발생하기 때문이다.
     * (예: 사용자A가 코스 [1,2]를, 사용자B가 [2,1]을 동시에 재계산하는 경우) 락 순서는 락을 잡는
     * 이 클래스가 스스로 보장한다 — 호출자에게 정렬을 요구하면 새 호출자가 생길 때마다 규약이 깨진다.</p>
     *
     * <p><b>호출자 계약</b> — 이 메서드는 {@code running_record}를 DB에서 직접 집계하므로, 호출 전에
     * 러닝 변경분(공개 여부 토글·소프트 삭제)이 <b>DB에 반영돼 있어야</b> 한다. 현재 두 호출 경로가
     * 성립하는 이유는 다음과 같다.
     * <ul>
     *   <li>러닝 삭제 — {@code deleteInRunningIds}가 {@code @Modifying} 벌크 JPQL이라 즉시 DB에 실행되고,
     *       {@code clearAutomatically}로 영속성 컨텍스트도 비워진다.</li>
     *   <li>러닝 공개 전환 — 더티체킹 변경분은 재계산 쿼리가 <b>네이티브</b>라서 플러시된다.
     *       Hibernate 는 동기화 쿼리 스페이스가 등록되지 않은 네이티브 쿼리 실행 전에 세션 전체를 플러시한다.</li>
     * </ul>
     * <b>경고</b> — 재계산 쿼리를 JPQL 로 바꾸면 이 보장이 사라진다. JPQL 의 자동 플러시는 쿼리가 건드리는
     * query space 에 미반영 변경이 있을 때만 발생하므로, 재계산이 변경 전 데이터로 수행돼 리드모델이
     * 조용히 옛 값으로 덮어써질 수 있다. 그때는 호출부에서 명시적으로 flush 해야 한다.</p>
     */
    public void recalculate(Collection<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return;
        }
        // 전역 락 순서(courseId 오름차순)를 고정해 데드락을 막는다.
        List<Long> lockOrderedCourseIds = courseIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        for (Long courseId : lockOrderedCourseIds) {
            readModelRepository.findByCourseIdForUpdate(courseId)
                    .ifPresent(this::recalculateReadModel);
        }
    }

    /**
     * 코스명 변경을 리드모델에 반영한다. (리드모델이 없는 코스는 건너뛴다)
     */
    public void rename(Long courseId, String name) {
        readModelRepository.findByCourseIdForUpdate(courseId)
                .ifPresent(readModel -> readModel.rename(name));
    }

    /**
     * 코스 공개 여부를 리드모델에 반영한다.
     *
     * 공개 전환 시 리드모델이 없으면 생성하고, 기존 러닝 전체로 TOP4·러너 수를 초기화한다.
     * 비공개 전환 시 리드모델이 없으면 아무 일도 하지 않는다.
     */
    public void syncPublicity(Long courseId, boolean isPublic) {
        CourseReadModel readModel = readModelRepository.findByCourseIdForUpdate(courseId).orElse(null);

        if (!isPublic) {
            if (readModel != null) {
                readModel.makePrivate();
            }
            return;
        }

        if (readModel == null) {
            createPublicReadModel(courseId);
            return;
        }
        readModel.makePublic();
    }

    /**
     * 코스 삭제에 맞춰 리드모델을 제거한다. (리드모델이 없는 코스는 건너뛴다)
     */
    public void delete(Long courseId) {
        readModelRepository.findByCourseIdForUpdate(courseId)
                .ifPresent(readModelRepository::delete);
    }

    /**
     * 공개 상태의 리드모델을 새로 만들고 기존 러닝 전체로 TOP4·러너 수를 채운다.
     */
    private void createPublicReadModel(Long courseId) {
        Course course = courseRepository.findByIdFetchJoinMember(courseId)
                .orElseThrow(() -> new CourseNotFoundException(ErrorCode.COURSE_NOT_FOUND, courseId));

        CourseReadModel readModel = readModelRepository.save(CourseReadModel.create(course));
        readModel.makePublic();
        recalculateReadModel(readModel);

        log.info("Created read model for course={}", courseId);
    }

    /**
     * running_record 집계로 TOP4 슬롯과 러너 수를 통째로 덮어쓴다. (집계 결과가 비면 슬롯도 전부 비워진다)
     */
    private void recalculateReadModel(CourseReadModel readModel) {
        Long courseId = readModel.getCourseId();

        List<RankSlot> topRankedSlots = readModelRepository.findTop4RunnersByBestDuration(courseId).stream()
                .map(this::toRankSlot)
                .toList();
        readModel.replaceTopRunners(new TopRunners(topRankedSlots));

        long runnersCount = readModelRepository.countDistinctPublicRunners(courseId);
        readModel.updateRunnersCount(runnersCount);

        // 재계산은 빈도가 낮지만 값을 통째로 덮어쓰는 연산이라, 사후에 "언제 무엇으로 덮였는지" 추적 가능해야 한다.
        log.info("Recalculated read model. course={}, topCount={}, runnersCount={}",
                courseId, topRankedSlots.size(), runnersCount);
    }

    /**
     * 판정 대상 러닝을 제외하고도 같은 멤버의 집계 대상 러닝이 남아있다면 첫 러닝이 아니다.
     */
    private boolean isMembersFirstPublicRun(Long courseId, Long memberId, Long runningId) {
        return !readModelRepository.existsOtherPublicRunByCourseAndMember(courseId, memberId, runningId);
    }

    private RankSlot toRankSlot(TopRunnerRow row) {
        return new RankSlot(row.getMemberId(), row.getBestDurationSeconds());
    }
}
