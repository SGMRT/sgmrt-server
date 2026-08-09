package soma.ghostrunner.domain.course.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.member.domain.Member;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CourseReadModel 순수 단위 테스트 (Spring 컨텍스트/DB 없음)
 *
 * 설계 문서: docs/refactoring/course-read-model/core/04-detailed-design.md §1-2, §1-5, §1-7
 *
 * 주요 테스트 대상:
 * - applyRun(): TOP4 증분 갱신 + 변경 감지(true/false)
 * - replaceTopRunners(): 재계산 결과 일괄 반영 (남는 슬롯은 비워짐)
 * - updateRunnersCount() / rename() / makePublic() / makePrivate()
 * - create(Course): 역정규화 필드(courseProfile, thumbnailUrl)까지 전체 매핑
 */
@DisplayName("CourseReadModel 단위 테스트")
class CourseReadModelTest {

    private static final String COURSE_NAME = "테스트 코스";
    private static final String OWNER_UUID = "owner-uuid";
    private static final CourseProfile COURSE_PROFILE = CourseProfile.of(5.0, 10.0, 100.0, -50.0);
    private static final CourseDataUrls COURSE_DATA_URLS = CourseDataUrls.of("route.url", "checkpoint.url", "thumb.url");
    private static final Coordinate START_COORDINATE = Coordinate.of(37.123, 127.123);

    @Nested
    @DisplayName("applyRun() - 러닝 종료 증분 갱신")
    class ApplyRun {

        @Test
        @DisplayName("TOP4보다 빠른 기록이면 true를 반환하고 슬롯이 갱신된다")
        void applyRunChangesTopRunners() {
            // given
            CourseReadModel readModel = createReadModel();
            readModel.replaceTopRunners(topRunners(slot(1L, 1000), slot(2L, 1100), slot(3L, 1200), slot(4L, 1300)));

            // when
            boolean changed = readModel.applyRun(5L, 900);

            // then
            assertThat(changed).isTrue();
            assertThat(readModel.getTop1()).isEqualTo(slot(5L, 900));
            assertThat(readModel.getTop2()).isEqualTo(slot(1L, 1000));
            assertThat(readModel.getTop3()).isEqualTo(slot(2L, 1100));
            assertThat(readModel.getTop4()).isEqualTo(slot(3L, 1200));
        }

        @Test
        @DisplayName("TOP4에 진입하지 못하는 기록이면 false를 반환하고 슬롯이 변하지 않는다")
        void applyRunWithoutChangeReturnsFalse() {
            // given
            CourseReadModel readModel = createReadModel();
            readModel.replaceTopRunners(topRunners(slot(1L, 1000), slot(2L, 1100), slot(3L, 1200), slot(4L, 1300)));

            // when
            boolean changed = readModel.applyRun(5L, 1400);

            // then
            assertThat(changed).isFalse();
            assertThat(readModel.getTop1()).isEqualTo(slot(1L, 1000));
            assertThat(readModel.getTop2()).isEqualTo(slot(2L, 1100));
            assertThat(readModel.getTop3()).isEqualTo(slot(3L, 1200));
            assertThat(readModel.getTop4()).isEqualTo(slot(4L, 1300));
        }

        @Test
        @DisplayName("이미 TOP4인 멤버가 더 빠른 기록을 내면 중복 없이 재정렬되고 true를 반환한다")
        void applyRunForExistingMemberReordersWithoutDuplicate() {
            // given
            CourseReadModel readModel = createReadModel();
            readModel.replaceTopRunners(topRunners(slot(1L, 1000), slot(2L, 1100), slot(3L, 1200), slot(4L, 1300)));

            // when
            boolean changed = readModel.applyRun(4L, 900);

            // then
            assertThat(changed).isTrue();
            assertThat(readModel.getTop1()).isEqualTo(slot(4L, 900));
            assertThat(readModel.getTop2()).isEqualTo(slot(1L, 1000));
            assertThat(readModel.getTop3()).isEqualTo(slot(2L, 1100));
            assertThat(readModel.getTop4()).isEqualTo(slot(3L, 1200));
        }

        @Test
        @DisplayName("슬롯이 비어 있는 리드모델에 첫 기록을 반영하면 top1만 채워진다")
        void applyRunOnEmptyReadModelFillsTop1() {
            // given
            CourseReadModel readModel = createReadModel();

            // when
            boolean changed = readModel.applyRun(1L, 1800);

            // then
            assertThat(changed).isTrue();
            assertThat(readModel.getTop1()).isEqualTo(slot(1L, 1800));
            assertThat(readModel.getTop2()).isNull();
            assertThat(readModel.getTop3()).isNull();
            assertThat(readModel.getTop4()).isNull();
        }
    }

    @Nested
    @DisplayName("replaceTopRunners() - 재계산 결과 일괄 반영")
    class ReplaceTopRunners {

        @Test
        @DisplayName("슬롯 4개를 반영하면 top1~top4에 순서대로 채워진다")
        void replaceWithFourSlots() {
            // given
            CourseReadModel readModel = createReadModel();

            // when
            readModel.replaceTopRunners(topRunners(slot(10L, 1000), slot(11L, 1100), slot(12L, 1200), slot(13L, 1300)));

            // then
            assertThat(readModel.getTop1()).isEqualTo(slot(10L, 1000));
            assertThat(readModel.getTop2()).isEqualTo(slot(11L, 1100));
            assertThat(readModel.getTop3()).isEqualTo(slot(12L, 1200));
            assertThat(readModel.getTop4()).isEqualTo(slot(13L, 1300));
        }

        @Test
        @DisplayName("슬롯 2개를 반영하면 top1~top2만 채워지고 남는 슬롯은 비워진다")
        void replaceWithTwoSlotsClearsRemaining() {
            // given
            CourseReadModel readModel = createReadModel();
            readModel.replaceTopRunners(topRunners(slot(1L, 1000), slot(2L, 1100), slot(3L, 1200), slot(4L, 1300)));

            // when
            readModel.replaceTopRunners(topRunners(slot(20L, 800), slot(21L, 900)));

            // then
            assertThat(readModel.getTop1()).isEqualTo(slot(20L, 800));
            assertThat(readModel.getTop2()).isEqualTo(slot(21L, 900));
            assertThat(readModel.getTop3()).isNull();
            assertThat(readModel.getTop4()).isNull();
        }

        @Test
        @DisplayName("빈 재계산 결과를 반영하면 모든 슬롯이 비워진다")
        void replaceWithEmptyClearsAllSlots() {
            // given
            CourseReadModel readModel = createReadModel();
            readModel.replaceTopRunners(topRunners(slot(1L, 1000), slot(2L, 1100), slot(3L, 1200), slot(4L, 1300)));

            // when
            readModel.replaceTopRunners(topRunners());

            // then
            assertThat(readModel.getTop1()).isNull();
            assertThat(readModel.getTop2()).isNull();
            assertThat(readModel.getTop3()).isNull();
            assertThat(readModel.getTop4()).isNull();
        }
    }

    @Nested
    @DisplayName("updateRunnersCount() - 러너 수 갱신")
    class UpdateRunnersCount {

        @Test
        @DisplayName("0 이상의 값은 그대로 반영된다")
        void updateWithValidCount() {
            // given
            CourseReadModel readModel = createReadModel();

            // when
            readModel.updateRunnersCount(10L);

            // then
            assertThat(readModel.getRunnersCount()).isEqualTo(10L);
        }

        @Test
        @DisplayName("음수는 무시되고 기존 값이 유지된다")
        void negativeCountIsIgnored() {
            // given
            CourseReadModel readModel = createReadModel();
            readModel.updateRunnersCount(10L);

            // when
            readModel.updateRunnersCount(-1L);

            // then
            assertThat(readModel.getRunnersCount()).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("rename() - 코스명 동기화")
    class Rename {

        @Test
        @DisplayName("유효한 이름이면 코스명이 변경된다")
        void renameWithValidName() {
            // given
            CourseReadModel readModel = createReadModel();

            // when
            readModel.rename("새로운 코스 이름");

            // then
            assertThat(readModel.getName()).isEqualTo("새로운 코스 이름");
        }

        @Test
        @DisplayName("null 이름은 무시되고 기존 코스명이 유지된다")
        void renameWithNullIsIgnored() {
            // given
            CourseReadModel readModel = createReadModel();

            // when
            readModel.rename(null);

            // then
            assertThat(readModel.getName()).isEqualTo(COURSE_NAME);
        }

        @Test
        @DisplayName("공백뿐인 이름은 무시되고 기존 코스명이 유지된다")
        void renameWithBlankIsIgnored() {
            // given
            CourseReadModel readModel = createReadModel();

            // when
            readModel.rename("   ");

            // then
            assertThat(readModel.getName()).isEqualTo(COURSE_NAME);
        }
    }

    @Nested
    @DisplayName("makePublic() / makePrivate() - 공개 상태 변경")
    class Publicity {

        @Test
        @DisplayName("makePublic()은 공개, makePrivate()은 비공개로 변경한다")
        void changePublicity() {
            // given
            CourseReadModel readModel = createReadModel();

            // when & then
            readModel.makePublic();
            assertThat(readModel.getIsPublic()).isTrue();

            readModel.makePrivate();
            assertThat(readModel.getIsPublic()).isFalse();
        }
    }

    @Nested
    @DisplayName("create(Course) - 코스 역정규화 매핑")
    class Create {

        @Test
        @DisplayName("코스 정보와 역정규화 필드(코스 프로필, 썸네일)까지 전부 매핑된다")
        void createMapsAllFields() {
            // given
            Course course = publicUserCourse(COURSE_NAME, 100L);

            // when
            CourseReadModel readModel = CourseReadModel.create(course);

            // then
            assertThat(readModel.getCourseId()).isEqualTo(100L);
            assertThat(readModel.getName()).isEqualTo(COURSE_NAME);
            assertThat(readModel.getOwnerUuid()).isEqualTo(OWNER_UUID);
            assertThat(readModel.getRouteUrl()).isEqualTo("route.url");
            assertThat(readModel.getThumbnailUrl()).isEqualTo("thumb.url");
            assertThat(readModel.getStartLat()).isEqualTo(37.123);
            assertThat(readModel.getStartLng()).isEqualTo(127.123);
            assertThat(readModel.getIsPublic()).isTrue();
            assertThat(readModel.getSource()).isEqualTo(CourseSource.USER);
            assertThat(readModel.getCourseProfile()).isNotNull();
            assertThat(readModel.getCourseProfile().getDistance()).isEqualTo(5.0);
            assertThat(readModel.getCourseProfile().getElevationAverage()).isEqualTo(10.0);
            assertThat(readModel.getCourseProfile().getElevationGain()).isEqualTo(100.0);
            assertThat(readModel.getCourseProfile().getElevationLoss()).isEqualTo(-50.0);
        }

        @Test
        @DisplayName("소유자가 없는 코스(OFFICIAL)는 ownerUuid가 null인 리드모델로 생성된다")
        void createWithoutOwnerLeavesOwnerUuidNull() {
            // given
            Course course = publicOfficialCourse("공식 코스", 200L);

            // when
            CourseReadModel readModel = CourseReadModel.create(course);

            // then
            assertThat(readModel.getOwnerUuid()).isNull();
            assertThat(readModel.getCourseId()).isEqualTo(200L);
            assertThat(readModel.getName()).isEqualTo("공식 코스");
            assertThat(readModel.getSource()).isEqualTo(CourseSource.OFFICIAL);
        }
    }

    // ========== Fixtures ==========

    /**
     * 슬롯이 비어 있는 공개 리드모델을 만든다.
     */
    private static CourseReadModel createReadModel() {
        return CourseReadModel.create(publicUserCourse(COURSE_NAME, 1L));
    }

    /**
     * 소유자가 있는 공개 코스 (source = USER)
     */
    private static Course publicUserCourse(String name, Long courseId) {
        Member owner = Member.of("러너", "profile.url");
        owner.setUuid(OWNER_UUID);
        Course course = Course.of(owner, name, COURSE_PROFILE, START_COORDINATE,
                CourseSource.USER, true, COURSE_DATA_URLS);
        ReflectionTestUtils.setField(course, "id", courseId);
        return course;
    }

    /**
     * 소유자가 없는 공식 코스 (source = OFFICIAL)
     */
    private static Course publicOfficialCourse(String name, Long courseId) {
        Course course = Course.of(name, COURSE_PROFILE, START_COORDINATE,
                CourseSource.OFFICIAL, true, COURSE_DATA_URLS);
        ReflectionTestUtils.setField(course, "id", courseId);
        return course;
    }

    private static TopRunners topRunners(RankSlot... slots) {
        return new TopRunners(List.of(slots));
    }

    private static RankSlot slot(long memberId, int timeSeconds) {
        return new RankSlot(memberId, timeSeconds);
    }
}
