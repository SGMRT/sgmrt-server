package soma.ghostrunner.domain.course.application;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import soma.ghostrunner.DatabaseCleanserExtension;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseDataUrls;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.domain.RankSlot;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * CourseReadModelWriter 통합 테스트
 *
 * 설계 문서: docs/refactoring/course-read-model/04-detailed-design.md
 * §0-3(쓰기 플로우), §1-6(runnersCount 정책), §3-1(Writer), §3-2(유즈케이스)
 *
 * 검증 대상은 Writer의 핵심 계약 5가지다.
 * 1. MANDATORY — 호출자 트랜잭션 없이는 실행되지 않는다 (조용히 안 타는 함정의 반대 성질)
 * 2. applyRun — 증분 갱신 + 첫 공개 러닝일 때만 runnersCount+1 (EXISTS 자기 제외 판정)
 * 3. recalculate — running_record를 진실의 원천으로 TOP4·runnersCount를 덮어써 드리프트를 보정
 * 4. syncPublicity / rename / delete — 리드모델 수명주기 동기화
 * 5. X락 — 동시 러닝 종료에서 Lost Update가 발생하지 않는다
 */
@DisplayName("CourseReadModelWriter 통합 테스트")
@ExtendWith(DatabaseCleanserExtension.class)
class CourseReadModelWriterTest extends IntegrationTestSupport {

    @Autowired
    private CourseReadModelWriter writer;

    @Autowired
    private CourseReadModelRepository readModelRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RunningRepository runningRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate requiresNewTemplate;

    @BeforeEach
    void setUp() {
        requiresNewTemplate = new TransactionTemplate(transactionManager);
        requiresNewTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    // ========== 1. 트랜잭션 계약 ==========

    @DisplayName("트랜잭션 없이 호출하면 예외를 던진다 — 리드모델 갱신은 호출자 트랜잭션 안에서만 일어난다")
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)  // 테스트 트랜잭션 비활성화
    void applyRun_withoutTransaction_throws() {
        // given : 활성 트랜잭션이 없는 상태 (DB 접근 이전에 막히므로 데이터는 필요 없다)

        // when & then
        assertThatThrownBy(() -> writer.applyRun(1L, 1L, 1800, 1L))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    // ========== 2. applyRun (증분) ==========

    @DisplayName("러닝 종료: TOP4에 기록이 반영되고 첫 공개 러닝이므로 러너 수가 1 증가한다")
    @Test
    void applyRun_reflectsRecordAndIncreasesRunnersCountOnFirstRun() {
        // given
        Member runner = saveMember("러너");
        Course course = savePublicCourse("한강 코스", runner);
        savePublicReadModel(course);
        Running running = savePublicRunning(runner, course, 1800L);
        flushAndClear();

        // when
        writer.applyRun(course.getId(), runner.getId(), 1800, running.getId());
        flushAndClear();

        // then
        CourseReadModel readModel = findReadModel(course.getId());
        assertThat(readModel.getTop1())
                .extracting(RankSlot::getMemberId, RankSlot::getTimeSeconds)
                .containsExactly(runner.getId(), 1800);
        assertThat(readModel.getTop2()).isNull();
        assertThat(readModel.getRunnersCount()).isEqualTo(1L);
    }

    @DisplayName("리드모델이 없는 코스(비공개)에 러닝이 종료되면 아무 일도 하지 않는다")
    @Test
    void applyRun_withoutReadModel_isNoOp() {
        // given : 리드모델을 만들지 않은 코스
        Member runner = saveMember("러너");
        Course course = savePrivateCourse("비공개 코스", runner);
        Running running = savePublicRunning(runner, course, 1800L);
        flushAndClear();

        // when & then : 예외 없이 통과하고 리드모델도 생기지 않는다
        assertThatCode(() -> writer.applyRun(course.getId(), runner.getId(), 1800, running.getId()))
                .doesNotThrowAnyException();
        flushAndClear();

        assertThat(readModelRepository.findByCourseId(course.getId())).isEmpty();
    }

    @DisplayName("같은 러너의 두 번째 러닝은 기록만 갱신하고 러너 수는 늘리지 않는다")
    @Test
    void applyRun_secondRunOfSameMember_keepsRunnersCount() {
        // given : 첫 러닝이 반영되어 러너 수가 1인 상태
        Member runner = saveMember("러너");
        Course course = savePublicCourse("한강 코스", runner);
        savePublicReadModel(course);
        Running firstRunning = savePublicRunning(runner, course, 3000L);
        flushAndClear();

        writer.applyRun(course.getId(), runner.getId(), 3000, firstRunning.getId());
        flushAndClear();
        assertThat(findReadModel(course.getId()).getRunnersCount()).isEqualTo(1L);

        // given : 같은 러너의 두 번째 러닝(더 빠른 기록)까지 저장된 상태
        Running secondRunning = savePublicRunning(runner, course, 2500L);
        flushAndClear();

        // when : 두 번째 러닝의 id로 판정 (자기 자신을 제외하면 첫 러닝이 남아 있다)
        writer.applyRun(course.getId(), runner.getId(), 2500, secondRunning.getId());
        flushAndClear();

        // then : TOP1은 갱신되지만 러너 수는 그대로
        CourseReadModel readModel = findReadModel(course.getId());
        assertThat(readModel.getTop1())
                .extracting(RankSlot::getMemberId, RankSlot::getTimeSeconds)
                .containsExactly(runner.getId(), 2500);
        assertThat(readModel.getRunnersCount()).isEqualTo(1L);
    }

    // ========== 3. recalculate (진실의 원천) ==========

    @DisplayName("재계산: 드리프트된 TOP4와 러너 수를 running_record 기준으로 정정한다")
    @Test
    void recalculate_correctsDriftedTopRunnersAndRunnersCount() {
        // given : 공개 러닝 3건 (1000 / 2000 / 3000초)
        Member first = saveMember("1등");
        Member second = saveMember("2등");
        Member third = saveMember("3등");
        Course course = savePublicCourse("한강 코스", first);
        savePublicRunning(first, course, 1000L);
        savePublicRunning(second, course, 2000L);
        savePublicRunning(third, course, 3000L);

        // given : 리드모델은 러닝 기록에 없는 유령 러너와 틀린 러너 수를 갖고 있다 (증분 드리프트 재현)
        Member ghost = saveMember("유령러너");
        CourseReadModel readModel = savePublicReadModel(course);
        readModel.applyRun(ghost.getId(), 500);
        readModel.updateRunnersCount(99L);
        flushAndClear();

        // when
        writer.recalculate(List.of(course.getId()));
        flushAndClear();

        // then : TOP4는 러닝 기록으로 재구성되고 러너 수는 COUNT로 보정된다
        CourseReadModel recalculated = findReadModel(course.getId());
        assertThat(List.of(recalculated.getTop1(), recalculated.getTop2(), recalculated.getTop3()))
                .extracting(RankSlot::getMemberId, RankSlot::getTimeSeconds)
                .containsExactly(
                        tuple(first.getId(), 1000),
                        tuple(second.getId(), 2000),
                        tuple(third.getId(), 3000));
        assertThat(recalculated.getTop4()).isNull();
        assertThat(recalculated.getRunnersCount()).isEqualTo(3L);
    }

    @DisplayName("재계산: 공개 러닝이 없으면 TOP4를 모두 비우고, 리드모델이 없는 코스는 건너뛴다")
    @Test
    void recalculate_clearsTopRunnersWhenNoPublicRunAndSkipsMissingReadModel() {
        // given : 러닝이 전부 사라진 상황 (기록만 리드모델에 남아 있음)
        Member runner = saveMember("러너");
        Course course = savePublicCourse("한강 코스", runner);
        CourseReadModel readModel = savePublicReadModel(course);
        readModel.applyRun(runner.getId(), 1800);
        readModel.updateRunnersCount(1L);

        Course courseWithoutReadModel = savePrivateCourse("리드모델 없는 코스", runner);
        flushAndClear();

        // when : 리드모델이 없는 courseId가 섞여 있어도 통과해야 한다
        assertThatCode(() -> writer.recalculate(List.of(course.getId(), courseWithoutReadModel.getId())))
                .doesNotThrowAnyException();
        flushAndClear();

        // then
        CourseReadModel recalculated = findReadModel(course.getId());
        assertThat(recalculated.getTop1()).isNull();
        assertThat(recalculated.getTop2()).isNull();
        assertThat(recalculated.getTop3()).isNull();
        assertThat(recalculated.getTop4()).isNull();
        assertThat(recalculated.getRunnersCount()).isZero();
        assertThat(readModelRepository.findByCourseId(courseWithoutReadModel.getId())).isEmpty();
    }

    // ========== 4. 리드모델 수명주기 ==========

    @DisplayName("코스 공개 전환: 리드모델이 없으면 생성하고 기존 러닝 전체로 TOP4와 러너 수를 초기화한다")
    @Test
    void syncPublicity_createsReadModelAndInitializesFromAllRunnings() {
        // given : 코스 주인보다 빠른 다른 러너가 이미 존재 (주인 기록만 채우면 실패한다)
        Member owner = saveMember("주인");
        Member fasterRunner = saveMember("더 빠른 러너");
        Course course = savePublicCourse("한강 코스", owner);
        savePublicRunning(owner, course, 2000L);
        savePublicRunning(fasterRunner, course, 1500L);
        flushAndClear();

        // when
        writer.syncPublicity(course.getId(), true);
        flushAndClear();

        // then : 생성 + 전체 재계산 결과가 담긴다
        CourseReadModel created = findReadModel(course.getId());
        assertThat(created.getIsPublic()).isTrue();
        assertThat(List.of(created.getTop1(), created.getTop2()))
                .extracting(RankSlot::getMemberId, RankSlot::getTimeSeconds)
                .containsExactly(
                        tuple(fasterRunner.getId(), 1500),
                        tuple(owner.getId(), 2000));
        assertThat(created.getRunnersCount()).isEqualTo(2L);
    }

    @DisplayName("코스 비공개 전환·이름 변경·삭제가 리드모델에 동기화된다")
    @Test
    void syncPublicity_rename_delete_synchronizeReadModel() {
        // given
        Member owner = saveMember("주인");
        Course course = savePublicCourse("옛 이름", owner);
        savePublicReadModel(course);
        flushAndClear();

        // when : 이름 변경
        writer.rename(course.getId(), "새 이름");
        flushAndClear();

        // then
        assertThat(findReadModel(course.getId()).getName()).isEqualTo("새 이름");

        // when : 비공개 전환
        writer.syncPublicity(course.getId(), false);
        flushAndClear();

        // then
        assertThat(findReadModel(course.getId()).getIsPublic()).isFalse();

        // when : 코스 삭제
        writer.delete(course.getId());
        flushAndClear();

        // then
        assertThat(readModelRepository.findByCourseId(course.getId())).isEmpty();
    }

    // ========== 5. 동시성 (X락) ==========

    @DisplayName("서로 다른 러너가 같은 코스에서 동시에 러닝을 마쳐도 두 기록이 모두 TOP4에 남는다")
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)  // 워커 트랜잭션이 볼 수 있도록 given을 커밋한다
    void applyRun_concurrentRunsOnSameCourse_keepsBothRecords() throws InterruptedException {
        // given : 커밋된 코스 + 리드모델 + 서로 다른 두 러너의 공개 러닝
        ConcurrentFixture fixture = requiresNewTemplate.execute(status -> {
            Member runnerA = saveMember("러너A");
            Member runnerB = saveMember("러너B");
            Course course = savePublicCourse("한강 코스", runnerA);
            savePublicReadModel(course);
            Running runningA = savePublicRunning(runnerA, course, 1800L);
            Running runningB = savePublicRunning(runnerB, course, 1900L);
            return new ConcurrentFixture(
                    course.getId(),
                    runnerA.getId(), runningA.getId(),
                    runnerB.getId(), runningB.getId());
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        // when : 두 스레드가 각자의 트랜잭션에서 동시에 증분 갱신
        try {
            executor.submit(applyRunTask(fixture.courseId(), fixture.memberAId(), 1800, fixture.runningAId(),
                    startLatch, doneLatch, failures));
            executor.submit(applyRunTask(fixture.courseId(), fixture.memberBId(), 1900, fixture.runningBId(),
                    startLatch, doneLatch, failures));

            startLatch.countDown();
            assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        // then : Lost Update 없이 두 기록이 모두 반영된다
        assertThat(failures).isEmpty();

        CourseReadModel readModel = requiresNewTemplate.execute(status ->
                readModelRepository.findByCourseId(fixture.courseId()).orElseThrow());
        assertThat(List.of(readModel.getTop1(), readModel.getTop2()))
                .extracting(RankSlot::getMemberId, RankSlot::getTimeSeconds)
                .containsExactly(
                        tuple(fixture.memberAId(), 1800),
                        tuple(fixture.memberBId(), 1900));
        assertThat(readModel.getRunnersCount()).isEqualTo(2L);
    }

    private Runnable applyRunTask(
            Long courseId, Long memberId, int durationSeconds, Long runningId,
            CountDownLatch startLatch, CountDownLatch doneLatch, List<Throwable> failures
    ) {
        return () -> {
            try {
                startLatch.await();
                requiresNewTemplate.executeWithoutResult(status ->
                        writer.applyRun(courseId, memberId, durationSeconds, runningId));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                failures.add(t);
            } finally {
                doneLatch.countDown();
            }
        };
    }

    private record ConcurrentFixture(
            Long courseId,
            Long memberAId, Long runningAId,
            Long memberBId, Long runningBId
    ) {
    }

    // ========== Helper Methods ==========

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private CourseReadModel findReadModel(Long courseId) {
        Optional<CourseReadModel> readModel = readModelRepository.findByCourseId(courseId);
        assertThat(readModel).isPresent();
        return readModel.get();
    }

    private Member saveMember(String nickname) {
        return memberRepository.save(Member.of(nickname, "https://example.com/" + nickname + ".jpg"));
    }

    private Course savePublicCourse(String name, Member owner) {
        return saveCourse(name, owner, true);
    }

    private Course savePrivateCourse(String name, Member owner) {
        return saveCourse(name, owner, false);
    }

    private Course saveCourse(String name, Member owner, boolean isPublic) {
        Course course = Course.of(
                owner,
                name,
                CourseProfile.of(5.0, 10.0, 100.0, 50.0),
                Coordinate.of(37.5, 127.0),
                CourseSource.USER,
                isPublic,
                CourseDataUrls.of(
                        "https://example.com/route.json",
                        "https://example.com/checkpoints.json",
                        "https://example.com/thumbnail.jpg")
        );
        return courseRepository.save(course);
    }

    private CourseReadModel savePublicReadModel(Course course) {
        CourseReadModel readModel = CourseReadModel.create(course);
        readModel.makePublic();
        return readModelRepository.save(readModel);
    }

    private Running savePublicRunning(Member member, Course course, Long durationSeconds) {
        RunningRecord record = RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 4.9, 6.9, durationSeconds, 302, 120, 56);
        Running running = Running.of("테스트 러닝", RunningMode.SOLO, null, record, 1750729987181L,
                true, false, "URL", "URL", "URL", member, course);
        return runningRepository.save(running);
    }
}
