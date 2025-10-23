package soma.ghostrunner.domain.course.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.member.domain.Member;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CourseReadModelTest {

    private CourseReadModel newSut() {
        Member owner = createMember("owner", "url://owner");
        Course course = createCourse(owner);
        return CourseReadModel.of(course);
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
                "route://1", "URL", "thumb://1");
        c.setIsPublic(true);
        return c;
    }

    private Coordinate createStartPoint() {
        return Coordinate.of(37.55, 127.01);
    }

    private CourseProfile createCourseProfile() {
        return CourseProfile.of(10.0, 12.3, 40.0, -10.0);
    }

    @DisplayName("TOP4 러너의 정보를 리스트로 업데이트한다.")
    @Test
    void updateRanking_allFour() {
        // given
        CourseReadModel sut = newSut();
        List<RankSlot> newRanks = List.of(
                RankSlot.of(101L, "profile://101"),
                RankSlot.of(202L, "profile://202"),
                RankSlot.of(303L, "profile://303"),
                RankSlot.of(404L, "profile://404")
        );

        // when
        sut.updateRanking(newRanks);
        sut.updateRunnersCount(4L);

        // then
        assertAll(
                () -> assertEquals(101L, sut.getRank1().getMemberId()),
                () -> assertEquals("profile://101", sut.getRank1().getMemberProfileUrl()),
                () -> assertEquals(202L, sut.getRank2().getMemberId()),
                () -> assertEquals("profile://202", sut.getRank2().getMemberProfileUrl()),
                () -> assertEquals(303L, sut.getRank3().getMemberId()),
                () -> assertEquals("profile://303", sut.getRank3().getMemberProfileUrl()),
                () -> assertEquals(404L, sut.getRank4().getMemberId()),
                () -> assertEquals("profile://404", sut.getRank4().getMemberProfileUrl()),

                // 랭커 외 필드 불변 확인
                () -> assertEquals(CourseSource.USER, sut.getSource()),
                () -> assertEquals(37.55, sut.getStartLatitude()),
                () -> assertEquals(127.01, sut.getStartLongitude()),
                () -> assertEquals(10.0, sut.getDistance()),
                () -> assertEquals(12.3, sut.getElevationAverage()),
                () -> assertEquals("route://1", sut.getRouteUrl()),
                () -> assertEquals("thumb://1", sut.getThumbnailUrl()),
                () -> assertEquals(4L, sut.getRunnersCount())
        );
    }

    @DisplayName("TOP4 러너 리스트가 2명 이하일 경우, 나머지는 null로 설정된다.")
    @Test
    void updateRanking_withLessThanFour() {
        // given
        CourseReadModel sut = newSut();
        List<RankSlot> partialRanks = List.of(
                RankSlot.of(101L, "profile://101"),
                RankSlot.of(202L, "profile://202")
        );

        // when
        sut.updateRanking(partialRanks);

        // then
        assertAll(
                // 1~2번은 세팅됨
                () -> assertEquals(101L, sut.getRank1().getMemberId()),
                () -> assertEquals("profile://101", sut.getRank1().getMemberProfileUrl()),
                () -> assertEquals(202L, sut.getRank2().getMemberId()),
                () -> assertEquals("profile://202", sut.getRank2().getMemberProfileUrl()),

                // 3~4번은 null로 초기화됨
                () -> assertNull(sut.getRank3()),
                () -> assertNull(sut.getRank4())
        );
    }

    @DisplayName("첫 번째 러너의 정보를 업데이트한다.")
    @Test
    void updateRanking_withFirstRunner() {
        // given
        CourseReadModel sut = newSut();
        RankSlot rankSlot = RankSlot.of(101L, "profile://101");

        // when
        sut.updateFirstRunner(rankSlot);

        // then
        assertAll(
                // 1번은 세팅됨
                () -> assertEquals(101L, sut.getRank1().getMemberId()),
                () -> assertEquals("profile://101", sut.getRank1().getMemberProfileUrl()),

                // 2~4번은 null로 초기화됨
                () -> assertNull(sut.getRank2()),
                () -> assertNull(sut.getRank3()),
                () -> assertNull(sut.getRank4())
        );
    }

}
