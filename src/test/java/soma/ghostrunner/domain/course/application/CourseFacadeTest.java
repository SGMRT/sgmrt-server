package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.dto.response.CourseDetailedResponse;
import soma.ghostrunner.domain.course.dto.response.CourseGhostResponse;
import soma.ghostrunner.domain.course.dto.response.CourseMapResponse;
import soma.ghostrunner.domain.course.enums.CourseSortType;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;

class CourseFacadeTest extends IntegrationTestSupport {

    private final double DEFAULT_LAT = 37, DEFAULT_LNG = 129;

    @Autowired
    private CourseFacade courseFacade;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RunningRepository runningRepository;

    private Member defaultMember;

    @BeforeEach
    void setUp() {
        defaultMember = memberRepository.save(Member.of("기본 회원", "test-url"));
    }

    @DisplayName("주변 코스를 검색하면, 각 코스별 상위 최대 4명의 러너 정보가 함께 조회된다.")
    @Test
    void findCoursesByPosition() {
        // given
        Course course1 = createCourse("유명한 코스");
        Course course2 = createCourse("적당히 달린 코스");
        Course course3 = createCourse("아무도 안 달린 코스");
        courseRepository.saveAll(List.of(course1, course2, course3));

        List<Member> memberPool = IntStream.range(0, 10).boxed()
                .map(i -> Member.of("회원" + i, "profile-url-" + i))
                .toList();
        memberRepository.saveAll(memberPool);
        saveDummyRunsToCourse(course1, 10, memberPool);
        saveDummyRunsToCourse(course2, 3, memberPool);
        saveDummyRunsToCourse(course3, 0, memberPool);

        // when
        List<CourseMapResponse> courses = courseFacade
                .findCoursesByPosition(DEFAULT_LAT, DEFAULT_LNG, 1000, CourseSortType.DISTANCE, null, defaultMember.getUuid());

        // then
        assertThat(courses.size()).isEqualTo(3);
        // 코스별 상위 러너 정보 검증
        assertThat(courses).extracting("runnersCount")
                .containsExactlyInAnyOrder(10L, 3L, 0L);
        List<CourseMapResponse.MemberRecord> memberPoolRecords = memberPool.stream()
                .map(member -> new CourseMapResponse.MemberRecord(member.getUuid(), member.getProfilePictureUrl()))
                .toList();
        assertThat(courses).extracting("runners")
                .containsExactlyInAnyOrder(
                        memberPoolRecords.stream().limit(4).toList(),
                        memberPoolRecords.stream().limit(3).toList(),
                        List.of());
    }

    @DisplayName("주변 코스 검색 결과 본인과 타인의 코스가 섞여있을 때, 소유권에 따른 개수 제한 정책을 올바르게 적용한다.")
    @ParameterizedTest(name = "[{index}] 내 코스 {0}개, 타인 코스 {1}개일 때 -> 내 코스 {2}개, 타인 코스 {3}개 반환")
    @MethodSource("provideCourseCountsForLimiting")
    void findCoursesByPosition_limitCount(int myCourseCount, int otherCourseCount, int expectedMyCourseCount, int expectedOtherCourseCount) {
        // given
        Member viewer = createMember("아이유");
        memberRepository.save(viewer);

        // 본인 소유 코스 생성
        List<Course> myCourses = IntStream.range(0, myCourseCount)
                .mapToObj(i -> createCourse("내 코스 " + i, viewer, DEFAULT_LAT + i * 0.0001, DEFAULT_LNG))
                .toList();
        courseRepository.saveAll(myCourses);

        // 타인 소유 코스 생성
        List<Course> otherCourses = IntStream.range(0, otherCourseCount)
                .mapToObj(i -> {
                    Member owner = saveMember(createMember("회원 " + i));
                    return createCourse("타인 코스 " + i, owner, DEFAULT_LAT - i * 0.0001, DEFAULT_LNG);
                })
                .toList();
        courseRepository.saveAll(otherCourses);

        // when
        List<CourseMapResponse> courses = courseFacade.findCoursesByPosition(DEFAULT_LAT, DEFAULT_LNG,
                5000, CourseSortType.DISTANCE, null, viewer.getUuid());

        // then
        long actualMyCourses = courses.stream()
                .filter(c -> c.ownerUuid().equals(viewer.getUuid()))
                .count();

        long actualOtherCourses = courses.stream()
                .filter(c -> !c.ownerUuid().equals(viewer.getUuid()))
                .count();

        assertThat(actualMyCourses).isEqualTo(expectedMyCourseCount);
        assertThat(actualOtherCourses).isEqualTo(expectedOtherCourseCount);
        assertThat(courses.size()).isEqualTo(expectedMyCourseCount + expectedOtherCourseCount);
    }

    private static Stream<Arguments> provideCourseCountsForLimiting() {
        return Stream.of(
                // 내 코스 5개, 타인 코스 5개 이상 -> 내 코스 5개, 타인 코스 5개
                Arguments.of(8, 12, 5, 5),
                // 내 코스 5개 미만, 타인 코스 5개 이상 -> 내 코스 모두, 타인 코스 7개
                Arguments.of(3, 10, 3, 7),
                // 내 코스 5개 이상, 타인 코스 5개 미만 -> 내 코스 (10 - 타인 코스 개수) 만큼, 타인 코스 모두
                Arguments.of(15, 3, 7, 3),
                // 내 코스와 타인 코스 합쳐서 10개 미만 -> 모든 코스 반환
                Arguments.of(4, 4, 4, 4),
                // 내 코스만 있는 경우 -> 최대 10개
                Arguments.of(12, 0, 10, 0),
                // 타인 코스만 있는 경우 -> 최대 10개
                Arguments.of(0, 12, 0, 10)
        );
    }

    @DisplayName("주변 코스 검색 시 코스를 달린 기록이 존재하는 경우에만 고스트 정보가 포함된다.")
    @Test
    void findCoursesByPosition_withRuns() {
        // given
        Course courseRan = createCourse("달린 코스");
        Course courseNotRan = createCourse("안 달린 코스");
        courseRepository.saveAll(List.of(courseRan, courseNotRan));

        Running myRunning = createRunning("나의 기록", courseRan, defaultMember);
        runningRepository.save(myRunning);

        // when
        List<CourseMapResponse> courses = courseFacade
                .findCoursesByPosition(DEFAULT_LAT, DEFAULT_LNG, 1000, CourseSortType.DISTANCE, null, defaultMember.getUuid());

        // then
        // 달린 코스의 고스트 응답 확인
        CourseMapResponse ranCourseResponse = courses.stream()
                .filter(c -> c.id().equals(courseRan.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(ranCourseResponse.myGhostInfo()).isNotNull();
        assertThat(ranCourseResponse.myGhostInfo().runningId()).isEqualTo(myRunning.getId());

        // 달리지 않은 코스의 고스트 응답 확인
        CourseMapResponse notRanCourseResponse = courses.stream()
                .filter(c -> c.id().equals(courseNotRan.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(notRanCourseResponse.myGhostInfo()).isNull();
    }

    @DisplayName("코스 상세 조회 시 코스를 달린 기록이 존재하는 경우에만 고스트 정보가 포함된다.")
    @Test
    void findCourse_withRuns() {
        // given
        Course course = createCourse("코스명");
        courseRepository.save(course);
        Member member1 = createMember("카리나");
        Member member2 = createMember("윈터");
        memberRepository.saveAll(List.of(member1, member2));
        Running running = createRunning("카리나의 기록", course, member1);
        runningRepository.save(running);

        // when
        CourseDetailedResponse responseWithGhostInfo = courseFacade.findCourse(course.getId(), member1.getUuid());
        CourseDetailedResponse responseWithoutGhostInfo = courseFacade.findCourse(course.getId(), member2.getUuid());

        // then
        // member1은 고스트 정보 존재
        CourseGhostResponse ghostStats = responseWithGhostInfo.myGhostInfo();
        assertNotNull(responseWithGhostInfo);
        assertThat(ghostStats.runningId()).isEqualTo(running.getId());
        assertThat(ghostStats.runnerUuid()).isEqualTo(member1.getUuid());
        // member2는 고스트 정보 존재 X
        assertThat(responseWithoutGhostInfo.myGhostInfo()).isNull();
    }

    @DisplayName("주변 코스 검색 시")
    @ParameterizedTest(name = "{0} 기준으로 정렬하면 그에 맞게 정렬되어야 한다.")
    @EnumSource(value = CourseSortType.class, names = {"DISTANCE", "POPULARITY"})
    void sortNearbyCourses(CourseSortType sortType) {
        // given
        Course courseNear = createCourse("가까운 코스", DEFAULT_LAT, DEFAULT_LNG);
        Course courseMid = createCourse("중간 코스", DEFAULT_LAT + 0.005, DEFAULT_LNG + 0.005);
        Course courseFar = createCourse("먼 코스", defaultMember, DEFAULT_LAT + 0.01, DEFAULT_LNG + 0.01);

        // 인기순: 러너가 많은 코스, 보통 코스, 적은 코스
        Course coursePopular = createCourse("인기 코스", DEFAULT_LAT + 0.015, DEFAULT_LNG + 0.015);
        Course courseNormal = createCourse("보통 코스", defaultMember,  DEFAULT_LAT + 0.02, DEFAULT_LNG + 0.02);
        Course courseUnpopular = createCourse("비인기 코스", DEFAULT_LAT + 0.025, DEFAULT_LNG + 0.025);

        // 최신순: 오래된 코스, 중간 코스, 최신 코스 (ID 오름차순으로 생성)
        courseRepository.saveAll(List.of(courseNear, courseMid, courseFar, coursePopular, courseNormal, courseUnpopular));

        // 인기순 정렬을 위한 더미 데이터 생성
        List<Member> memberPool = IntStream.range(0, 10).boxed()
                .map(i -> createMember("회원" + i))
                .toList();
        memberRepository.saveAll(memberPool);
        saveDummyRunsToCourse(coursePopular, 10, memberPool); // 인기 코스: 10명
        saveDummyRunsToCourse(courseNormal, 5, memberPool); // 보통 코스: 5명
        saveDummyRunsToCourse(courseUnpopular, 3, memberPool); // 비인기 코스: 1명
        saveDummyRunsToCourse(courseNear, 2, memberPool);
        saveDummyRunsToCourse(courseMid, 1, memberPool);
        saveDummyRunsToCourse(courseFar, 0, memberPool);

        // when
        List<CourseMapResponse> actualCourses = courseFacade
                .findCoursesByPosition(DEFAULT_LAT, DEFAULT_LNG, 19999, sortType, null, defaultMember.getUuid());

        // then
        List<String> actualCourseNames = actualCourses.stream()
                .map(CourseMapResponse::name)
                .toList();

        // 정렬 기준(sortType)에 따라 기대되는 이름 순서를 정의하고 실제 결과와 비교
        switch (sortType) {
            case DISTANCE -> {
                List<String> expectedOrder = List.of("가까운 코스", "중간 코스", "먼 코스", "인기 코스", "보통 코스", "비인기 코스");
                assertThat(actualCourseNames).containsSequence(expectedOrder);
            }
            case POPULARITY -> {
                List<String> expectedOrder = List.of("인기 코스", "보통 코스", "비인기 코스", "가까운 코스", "중간 코스", "먼 코스");
                assertThat(actualCourseNames).containsSequence(expectedOrder);
            }
        }
    }

    // --- Helper Methods ---
    private Course createCourse(String name) {
        return createCourse(name, defaultMember, DEFAULT_LAT, DEFAULT_LNG, CourseProfile.of(100d, 10d, 10d, -10d));
    }

    private Course createCourse(String name, double lat, double lng) {
        return createCourse(name, defaultMember, lat, lng, CourseProfile.of(100d, 10d, 10d, -10d));
    }

    private Course createCourse(String name, Member member, double lat, double lng) {
        Course course = Course.of(member, 0d, 0d, 0d, 0d, lat, lng, "url", "url", "url");
        course.setName(name);
        course.setIsPublic(true);
        return course;
    }

    private Course createCourse(String name, Member member, double lat, double lng, CourseProfile courseProfile) {
        Course course = Course.of(member, 0d, 0d, 0d, 0d, lat, lng, "url", "url", "url");
        course.setName(name);
        course.setIsPublic(true);
        return course;
    }

    private RunningRecord createRunningRecord() {
        return RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 3423.2, 302.2, 120L, 56, 100, 120);
    }

    private RunningRecord createRunningRecord(Long runDuration) {
        return RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 3423.2, 302.2, runDuration, 56, 100, 120);
    }

    private Running createRunning(String runningName, Course course, Member member) {
        return Running.of(
                runningName, RunningMode.SOLO, null,
                createRunningRecord(), 1750729987181L,
                true, false,
                "Raw Telemetry Mock URL", "Interpolated Mock URL", "screenShot",
                member, course
        );
    }

    private Running createRunning(String runningName, Course course, Member member, Long runDuration) {
        return Running.of(
                runningName, RunningMode.SOLO, null,
                createRunningRecord(runDuration), 1750729987181L,
                true, false,
                "Raw Telemetry Mock URL", "Interpolated Mock URL", "screenShot",
                member, course
        );
    }

    private Member createMember(String nickname) {
        return Member.of(nickname, "picture-url");
    }

    private Member saveMember(Member member) {
        return memberRepository.save(member);
    }

    // 코스에 더미 러닝기록을 n개 저장한다 (러닝 성적은 i = 0~n으로 갈수록 낮아진다)
    private void saveDummyRunsToCourse(Course course, int runsToSave, List<Member> memberPool) {
        long initialRunDuration = 3600L;
        for (int i = 0; i < runsToSave; i++) {
            runningRepository.save(
                    createRunning("러닝" + i, course, memberPool.get(i %  memberPool.size()), initialRunDuration + 60)
            );
        }
    }

}