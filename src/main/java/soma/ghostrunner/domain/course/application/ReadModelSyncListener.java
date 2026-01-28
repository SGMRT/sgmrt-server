package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;
import soma.ghostrunner.domain.running.domain.events.RunFinishedEvent;

/**
 * 리드모델 동기화 리스너
 *
 * 역할:
 * - 러닝 저장 시 리드모델 갱신 (증분 갱신)
 * - 같은 트랜잭션 내에서 실행 (BEFORE_COMMIT)
 * - Running 저장과 ReadModel 갱신이 원자적으로 처리됨
 *
 * 동시성 제어:
 * - X락 (FOR UPDATE)으로 Lost Update 방지
 * - REPEATABLE READ 격리 수준 유지 (증분 갱신이므로 문제없음)
 *
 * 트랜잭션 전파:
 * - Running 저장 트랜잭션 커밋 전에 실행됨
 * - 실패 시 Running 저장도 함께 롤백됨 (일관성 보장)
 *
 * 리드모델 생성 책임:
 * - 리드모델은 코스 등록 시점에 CourseService에서 생성됨
 * - 이 리스너는 기존 리드모델의 동기화만 담당
 * - 리드모델이 없으면 동기화를 스킵함
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadModelSyncListener {

    private final CourseReadModelRepository readModelRepository;
    private final RunningRepository runningRepository;

    /**
     * 러닝 완료 이벤트 처리
     *
     * - Running 저장 트랜잭션 커밋 전에 실행됨 (BEFORE_COMMIT)
     * - 같은 트랜잭션 내에서 실행되어 원자성 보장
     * - 리드모델에 TOP4 증분 갱신
     * - 리드모델이 없으면 스킵 (코스 등록 시점에 생성되어야 함)
     * - runners_count 업데이트
     *
     * @param event RunFinishedEvent
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleRunFinished(RunFinishedEvent event) {
        Long courseId = event.courseId();
        Long memberId = event.memberId();
        Integer durationSeconds = event.durationSeconds();

        // 코스가 없거나 시간 정보가 없으면 스킵
        if (courseId == null || memberId == null || durationSeconds == null) {
            log.debug("Skip read model sync: courseId={}, memberId={}, duration={}",
                courseId, memberId, durationSeconds);
            return;
        }

        // 리드모델 조회 (X락)
        CourseReadModel readModel = readModelRepository
            .findByCourseIdForUpdate(courseId)
            .orElse(null);

        // 리드모델이 없으면 스킵 (코스 등록 시점에 생성되어야 함)
        if (readModel == null) {
            log.warn("ReadModel not found for course={}. Skipping sync.", courseId);
            return;
        }

        // 증분 갱신
        boolean inserted = readModel.insertIfBetter(memberId, durationSeconds);
        if (inserted) {
            log.info("Updated TOP4 for course={}: member={}, time={}s",
                courseId, memberId, durationSeconds);
        }

        // runners_count 업데이트
        long runnersCount = runningRepository.countDistinctRunnersByCourseId(courseId);
        readModel.updateRunnersCount(runnersCount);

        // 저장
        readModelRepository.save(readModel);
    }
}
