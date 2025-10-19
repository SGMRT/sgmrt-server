package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dto.CourseRankInfo;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CourseRankFinderTest extends IntegrationTestSupport {

    @Autowired
    private CourseRankFinder courseRankFinder;

    @Autowired
    private EntityManager em;

    @DisplayName("코스별 멤버의 최소 러닝시간 기준 상위 4명을 (minDuration ASC)으로 조회한다")
    @Test
    void findCourseTop4RankInfoByCourseId_ASC() {
        // given
        Member owner = createMember("owner", "url://owner");
        em.persist(owner);
        Course course = createCourse(owner);
        em.persist(course);

        // 멤버 생성
        Member m1 = persistMember("m1", "url://m1");
        Member m2 = persistMember("m2", "url://m2");
        Member m3 = persistMember("m3", "url://m3");
        Member m4 = persistMember("m4", "url://m4");
        Member m5 = persistMember("m5", "url://m5");

        // 멤버별 최소 duration 값
        // m1: 300
        persistRunningWithDuration(m1, course, 500L, true);
        persistRunningWithDuration(m1, course, 300L, true);
        // m2: 280
        persistRunningWithDuration(m2, course, 360L, true);
        persistRunningWithDuration(m2, course, 280L, true);
        // m3: 400
        persistRunningWithDuration(m3, course, 700L, true);
        persistRunningWithDuration(m3, course, 400L, true);
        // m4: 200
        persistRunningWithDuration(m4, course, 200L, true);
        // m5: 100 (가장 빠르지만 상위 4명만 조회)
        persistRunningWithDuration(m5, course, 100L, true);
        // 비공개 러닝 → 제외되어야 함
        persistRunningWithDuration(m5, course, 9999L, false);

        em.flush();
        em.clear();

        // when
        List<CourseRankInfo> result =
                courseRankFinder.findCourseTop4RankInfoByCourseId(course.getId());

        // then
        assertEquals(4, result.size(), "상위 4명만 반환되어야 한다");

        // ASC 기준: [m5(100), m4(200), m2(280), m1(300)]
        assertAll(
                () -> assertEquals(course.getId(), result.get(0).getCourseId()),
                () -> assertEquals(m5.getId(), result.get(0).getMemberId()),
                () -> assertEquals(100L, result.get(0).getDuration()),
                () -> assertEquals("url://m5", result.get(0).getMemberProfileUrl()),

                () -> assertEquals(m4.getId(), result.get(1).getMemberId()),
                () -> assertEquals(200L, result.get(1).getDuration()),
                () -> assertEquals("url://m4", result.get(1).getMemberProfileUrl()),

                () -> assertEquals(m2.getId(), result.get(2).getMemberId()),
                () -> assertEquals(280L, result.get(2).getDuration()),
                () -> assertEquals("url://m2", result.get(2).getMemberProfileUrl()),

                () -> assertEquals(m1.getId(), result.get(3).getMemberId()),
                () -> assertEquals(300L, result.get(3).getDuration()),
                () -> assertEquals("url://m1", result.get(3).getMemberProfileUrl())
        );

        // m3(400)은 5번째로 제외되어야 함
        assertTrue(result.stream().noneMatch(r -> r.getMemberId().equals(m3.getId())));
    }

    /* ====== 헬퍼 ====== */

    private Member persistMember(String name, String profileUrl) {
        Member m = createMember(name, profileUrl);
        em.persist(m);
        return m;
    }

    private void persistRunningWithDuration(Member member, Course course, long durationSec, boolean isPublic) {
        Running running = createRunningWithDuration(member, course, durationSec, isPublic);
        em.persist(running);
    }

    private Running createRunningWithDuration(Member testMember, Course testCourse, long durationSec, boolean isPublic) {
        RunningRecord rr = RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 4.9, 6.9, durationSec, 302, 120, 56);

        return Running.of("테스트 러닝", RunningMode.SOLO, null, rr, 1750729987181L,
                isPublic, false, "URL", "URL", "URL", testMember, testCourse);
    }

    private Member createMember(String name, String profileUrl) {
        return Member.of(name, profileUrl);
    }

    private Course createCourse(Member testMember) {
        CourseProfile p = createCourseProfile();
        Coordinate start = createStartPoint();
        Course c = Course.of(testMember, p.getDistance(), p.getElevationAverage(),
                p.getElevationGain(), p.getElevationLoss(),
                start.getLatitude(), start.getLongitude(),
                "URL", "URL", "URL");
        c.setIsPublic(true);
        return c;
    }

    private Coordinate createStartPoint() {
        return Coordinate.of(37.545354, 34.7878);
    }

    private CourseProfile createCourseProfile() {
        return CourseProfile.of(5.2, 30.0, 40.0, -10.0);
    }
}
