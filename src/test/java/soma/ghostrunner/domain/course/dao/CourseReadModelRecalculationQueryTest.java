package soma.ghostrunner.domain.course.dao;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseDataUrls;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.dto.query.TopRunnerRow;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * CourseReadModelRepository 재계산/판정 쿼리 통합 테스트
 *
 * 설계 문서: docs/refactoring/course-read-model/04-detailed-design.md §3-3(재계산 쿼리), §1-6(runnersCount 정책)
 *
 * 집계 공통 필터: is_public = TRUE AND deleted = FALSE AND has_paused = FALSE
 */
@DisplayName("CourseReadModel 재계산 쿼리 통합 테스트")
class CourseReadModelRecalculationQueryTest extends IntegrationTestSupport {

    @Autowired
    private CourseReadModelRepository readModelRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private RunningRepository runningRepository;

    @Autowired
    private EntityManager em;

    @DisplayName("TOP4 재계산: 멤버별 최고 기록으로 집계해 기록 오름차순 상위 4명만 반환한다")
    @Test
    void findTop4RunnersByBestDuration_aggregatesBestPerMemberAndLimitsToFour() {
        // given : 멤버 5명, 공개 러닝 6건 (러너2는 2건 - 느린 기록 + 가장 빠른 기록)
        Member runner1 = saveMember("러너1");
        Member runner2 = saveMember("러너2");
        Member runner3 = saveMember("러너3");
        Member runner4 = saveMember("러너4");
        Member runner5 = saveMember("러너5");
        Course course = saveCourse("한강 코스", runner1);

        savePublicRunning(runner1, course, 3000L);
        savePublicRunning(runner2, course, 2000L);
        savePublicRunning(runner2, course, 1000L);  // 러너2의 최고 기록
        savePublicRunning(runner3, course, 2500L);
        savePublicRunning(runner4, course, 4000L);  // 5위 - 잘려야 함
        savePublicRunning(runner5, course, 3500L);
        flushAndClear();

        // when
        List<TopRunnerRow> rows = readModelRepository.findTop4RunnersByBestDuration(course.getId());

        // then : 멤버 중복 없이 MIN(duration) 기준 오름차순 4행
        assertThat(rows)
                .hasSize(4)
                .extracting(TopRunnerRow::getMemberId, TopRunnerRow::getBestDurationSeconds)
                .containsExactly(
                        tuple(runner2.getId(), 1000),
                        tuple(runner3.getId(), 2500),
                        tuple(runner1.getId(), 3000),
                        tuple(runner5.getId(), 3500)
                );
    }

    @DisplayName("TOP4 재계산: 비공개·삭제·일시정지 러닝은 집계에서 제외된다")
    @Test
    void findTop4RunnersByBestDuration_excludesPrivateDeletedAndPausedRunnings() {
        // given : 집계 대상 2건 + 그보다 빠르지만 제외되어야 할 3건(비공개 / 삭제 / 일시정지)
        Member normal1 = saveMember("정상1");
        Member normal2 = saveMember("정상2");
        Course course = saveCourse("한강 코스", normal1);

        savePublicRunning(normal1, course, 3000L);
        savePublicRunning(normal2, course, 3500L);

        saveRunningsExcludedFromAggregation(course);
        flushAndClear();

        // when
        List<TopRunnerRow> rows = readModelRepository.findTop4RunnersByBestDuration(course.getId());

        // then : 제외 대상 3건이 빠지고 정상 러닝 2건만 남는다
        assertThat(rows)
                .hasSize(2)
                .extracting(TopRunnerRow::getMemberId, TopRunnerRow::getBestDurationSeconds)
                .containsExactly(
                        tuple(normal1.getId(), 3000),
                        tuple(normal2.getId(), 3500)
                );
    }

    @DisplayName("러너 수 재계산: 동일 필터 기준으로 서로 다른 멤버 수를 집계한다")
    @Test
    void countDistinctPublicRunners_countsDistinctMembersWithSameFilter() {
        // given : 정상 멤버 2명(한 명은 러닝 2건) + 비공개/삭제/일시정지 멤버 3명
        Member normal1 = saveMember("정상1");
        Member normal2 = saveMember("정상2");
        Course course = saveCourse("한강 코스", normal1);

        savePublicRunning(normal1, course, 3000L);
        savePublicRunning(normal1, course, 3200L);  // 같은 멤버의 두 번째 러닝
        savePublicRunning(normal2, course, 3500L);

        saveRunningsExcludedFromAggregation(course);
        flushAndClear();

        // when
        long count = readModelRepository.countDistinctPublicRunners(course.getId());

        // then
        assertThat(count).isEqualTo(2L);
    }

    @DisplayName("첫 러닝 판정: 방금 저장한 러닝을 제외하면 다른 공개 러닝이 있을 때만 true를 반환한다")
    @Test
    void existsOtherPublicRunByCourseAndMember_excludesGivenRunningId() {
        // given : 멤버의 유일한 공개 러닝
        Member member = saveMember("러너");
        Course course = saveCourse("한강 코스", member);
        Running firstRunning = savePublicRunning(member, course, 3000L);
        flushAndClear();

        // when : 자기 자신을 제외하면 남는 러닝이 없다
        boolean existsBeforeSecondRun = readModelRepository.existsOtherPublicRunByCourseAndMember(
                course.getId(), member.getId(), firstRunning.getId());

        // then
        assertThat(existsBeforeSecondRun).isFalse();

        // given : 같은 코스에 두 번째 공개 러닝 추가
        Running secondRunning = savePublicRunning(member, course, 2800L);
        flushAndClear();

        // when : 두 번째 러닝을 제외해도 첫 러닝이 남는다
        boolean existsAfterSecondRun = readModelRepository.existsOtherPublicRunByCourseAndMember(
                course.getId(), member.getId(), secondRunning.getId());

        // then
        assertThat(existsAfterSecondRun).isTrue();
    }

    @DisplayName("러닝이 없는 코스는 빈 TOP4와 러너 수 0을 반환한다")
    @Test
    void emptyCourse_returnsNoTopRunnersAndZeroRunnerCount() {
        // given
        Member member = saveMember("러너");
        Course course = saveCourse("빈 코스", member);
        flushAndClear();

        // when
        List<TopRunnerRow> rows = readModelRepository.findTop4RunnersByBestDuration(course.getId());
        long count = readModelRepository.countDistinctPublicRunners(course.getId());

        // then
        assertThat(rows).isEmpty();
        assertThat(count).isZero();
    }

    // ========== Helper Methods ==========

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private Member saveMember(String nickname) {
        return memberRepository.save(Member.of(nickname, "https://example.com/" + nickname + ".jpg"));
    }

    private Course saveCourse(String name, Member owner) {
        Course course = Course.of(
                owner,
                name,
                CourseProfile.of(5.0, 10.0, 100.0, 50.0),
                Coordinate.of(37.5, 127.0),
                CourseSource.USER,
                true,
                CourseDataUrls.of(
                        "https://example.com/route.json",
                        "https://example.com/checkpoints.json",
                        "https://example.com/thumbnail.jpg")
        );
        return courseRepository.save(course);
    }

    private Running savePublicRunning(Member member, Course course, Long durationSeconds) {
        return saveRunning(member, course, durationSeconds, true, false);
    }

    /**
     * 집계 대상에서 빠져야 하는 러닝 3건(비공개 / 삭제 / 일시정지)을 저장한다.
     * 세 건 모두 정상 러닝보다 빠른 기록이므로, 집계에 섞이면 TOP4·러너 수 검증이 반드시 실패한다.
     */
    private void saveRunningsExcludedFromAggregation(Course course) {
        saveRunning(saveMember("비공개러너"), course, 500L, false, false);
        saveRunning(saveMember("일시정지러너"), course, 900L, true, true);

        Running deletedRunning = saveRunning(saveMember("삭제러너"), course, 800L, true, false);
        // 소프트 삭제 (프로덕션과 동일한 벌크 경로 — 컬렉션 cascade 회피)
        runningRepository.deleteInRunningIds(List.of(deletedRunning.getId()));
    }

    private Running saveRunning(Member member, Course course, Long durationSeconds, boolean isPublic, boolean hasPaused) {
        RunningRecord record = RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 4.9, 6.9, durationSeconds, 302, 120, 56);
        Running running = Running.of("테스트 러닝", RunningMode.SOLO, null, record, 1750729987181L,
                isPublic, hasPaused, "URL", "URL", "URL", member, course);
        return runningRepository.save(running);
    }
}
