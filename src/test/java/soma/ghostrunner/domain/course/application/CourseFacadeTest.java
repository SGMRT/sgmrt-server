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
import soma.ghostrunner.domain.course.dao.CourseCacheRepository;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.dto.RunnerProfile;
import soma.ghostrunner.domain.course.dto.query.CourseQueryModel;
import soma.ghostrunner.domain.course.dto.response.CourseMapResponse;
import soma.ghostrunner.domain.course.enums.CourseSortType;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import java.lang.reflect.Field;
import java.util.ArrayList;
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

    @Autowired
    private CourseCacheRepository courseCacheRepository;

    private Member defaultMember;

    @BeforeEach
    void setUp() {
        defaultMember = memberRepository.save(Member.of("기본 회원", "test-url"));
    }

//    @DisplayName("주변 코스를 검색하면, 각 코스별 상위 최대 4명의 러너 정보가 함께 조회된다.")
//    @Test
//    void findCoursesByPosition() {
//        // given
//        Course course1 = createCourse("유명한 코스");
//        Course course2 = createCourse("적당히 달린 코스");
//        Course course3 = createCourse("아무도 안 달린 코스");
//        courseRepository.saveAll(List.of(course1, course2, course3));
//
//        List<Member> memberPool = IntStream.range(0, 10).boxed()
//                .map(i -> Member.of("회원" + i, "profile-url-" + i))
//                .toList(); // {회원0, 회원1, ..., 회원9}
//        memberRepository.saveAll(memberPool);
//
//        // 코스별 러닝 생성 (코스1 = 10개, 코스2 = 3개, 코스3 = 0개)
//        saveDummyRunsToCourse(course1, 10, memberPool);
//        saveDummyRunsToCourse(course2, 3, memberPool);
//        saveDummyRunsToCourse(course3, 0, memberPool);
//
//        // when
//        List<CourseMapResponse> courses = courseFacade
//                .findCoursesByPosition(DEFAULT_LAT, DEFAULT_LNG, 1000, CourseSortType.DISTANCE, null, defaultMember.getUuid());
//
//        // then
//        assertThat(courses.size()).isEqualTo(3);
//        // 코스별 상위 러너 정보 검증
//        assertThat(courses).extracting("runnersCount")
//                .containsExactlyInAnyOrder(10L, 3L, 0L);
//        List<RunnerProfile> memberPoolRecords = memberPool.stream()
//                .map(member -> new RunnerProfile(member.getUuid(), member.getProfilePictureUrl()))
//                .toList();
//        assertThat(courses).extracting("runners")
//                .containsExactlyInAnyOrder(
//                        memberPoolRecords.stream().limit(4).toList(),
//                        memberPoolRecords.stream().limit(3).toList(),
//                        List.of());
//    }
//
//    @DisplayName("주변 코스 검색 결과 본인과 타인의 코스가 섞여있을 때, 소유권에 따른 개수 제한 정책을 올바르게 적용한다.")
//    @ParameterizedTest(name = "[{index}] 내 코스 {0}개, 타인 코스 {1}개일 때 -> 내 코스 {2}개, 타인 코스 {3}개 반환")
//    @MethodSource("provideCourseCountsForLimiting")
//    void findCoursesByPosition_limitCount(int myCourseCount, int otherCourseCount, int expectedMyCourseCount, int expectedOtherCourseCount) {
//        // given
//        Member viewer = createMember("아이유");
//        memberRepository.save(viewer);
//
//        // 본인 소유 코스 생성
//        List<Course> myCourses = IntStream.range(0, myCourseCount)
//                .mapToObj(i -> createCourse("내 코스 " + i, viewer, DEFAULT_LAT + i * 0.0001, DEFAULT_LNG))
//                .toList();
//        courseRepository.saveAll(myCourses);
//
//        // 타인 소유 코스 생성
//        List<Course> otherCourses = IntStream.range(0, otherCourseCount)
//                .mapToObj(i -> {
//                    Member owner = saveMember(createMember("회원 " + i));
//                    return createCourse("타인 코스 " + i, owner, DEFAULT_LAT - i * 0.0001, DEFAULT_LNG);
//                })
//                .toList();
//        courseRepository.saveAll(otherCourses);
//
//        // when
//        List<CourseMapResponse> courses = courseFacade.findCoursesByPosition(DEFAULT_LAT, DEFAULT_LNG,
//                5000, CourseSortType.DISTANCE, null, viewer.getUuid());
//
//        // then
//        long actualMyCourses = courses.stream()
//                .filter(c -> c.ownerUuid().equals(viewer.getUuid()))
//                .count();
//
//        long actualOtherCourses = courses.stream()
//                .filter(c -> !c.ownerUuid().equals(viewer.getUuid()))
//                .count();
//
//        assertThat(actualMyCourses).isEqualTo(expectedMyCourseCount);
//        assertThat(actualOtherCourses).isEqualTo(expectedOtherCourseCount);
//        assertThat(courses.size()).isEqualTo(expectedMyCourseCount + expectedOtherCourseCount);
//    }
//
//    private static Stream<Arguments> provideCourseCountsForLimiting() {
//        return Stream.of(
//                // 내 코스 5개, 타인 코스 5개 이상 -> 내 코스 5개, 타인 코스 5개
//                Arguments.of(8, 12, 5, 5),
//                // 내 코스 5개 미만, 타인 코스 5개 이상 -> 내 코스 모두, 타인 코스 7개
//                Arguments.of(3, 10, 3, 7),
//                // 내 코스 5개 이상, 타인 코스 5개 미만 -> 내 코스 (10 - 타인 코스 개수) 만큼, 타인 코스 모두
//                Arguments.of(15, 3, 7, 3),
//                // 내 코스와 타인 코스 합쳐서 10개 미만 -> 모든 코스 반환
//                Arguments.of(4, 4, 4, 4),
//                // 내 코스만 있는 경우 -> 최대 10개
//                Arguments.of(12, 0, 10, 0),
//                // 타인 코스만 있는 경우 -> 최대 10개
//                Arguments.of(0, 12, 0, 10)
//        );
//    }
//
//    @DisplayName("주변 코스 검색 시 코스를 달린 기록이 존재하는 경우에만 고스트 정보가 포함된다.")
//    @Test
//    void findCoursesByPosition_withRuns() {
//        // given
//        Course courseRan = createCourse("달린 코스");
//        Course courseNotRan = createCourse("안 달린 코스");
//        courseRepository.saveAll(List.of(courseRan, courseNotRan));
//
//        Running myRunning = createRunning("나의 기록", courseRan, defaultMember);
//        runningRepository.save(myRunning);
//
//        // when
//        List<CourseMapResponse> courses = courseFacade
//                .findCoursesByPosition(DEFAULT_LAT, DEFAULT_LNG, 1000, CourseSortType.DISTANCE, null, defaultMember.getUuid());
//
//        // then
//        // 달린 코스의 고스트 응답 확인
//        CourseMapResponse ranCourseResponse = courses.stream()
//                .filter(c -> c.id().equals(courseRan.getId()))
//                .findFirst()
//                .orElseThrow();
//        assertThat(ranCourseResponse.myGhostInfo()).isNotNull();
//        assertThat(ranCourseResponse.myGhostInfo().runningId()).isEqualTo(myRunning.getId());
//
//        // 달리지 않은 코스의 고스트 응답 확인
//        CourseMapResponse notRanCourseResponse = courses.stream()
//                .filter(c -> c.id().equals(courseNotRan.getId()))
//                .findFirst()
//                .orElseThrow();
//        assertThat(notRanCourseResponse.myGhostInfo()).isNull();
//    }
//
//    @DisplayName("코스 상세 조회 시 코스를 달린 기록이 존재하는 경우에만 고스트 정보가 포함된다.")
//    @Test
//    void findCourse_withRuns() {
//        // given
//        Course course = createCourse("코스명");
//        courseRepository.save(course);
//        Member member1 = createMember("카리나");
//        Member member2 = createMember("윈터");
//        memberRepository.saveAll(List.of(member1, member2));
//        Running running = createRunning("카리나의 기록", course, member1);
//        runningRepository.save(running);
//
//        // when
//        CourseDetailedResponse responseWithGhostInfo = courseFacade.findCourse(course.getId(), member1.getUuid());
//        CourseDetailedResponse responseWithoutGhostInfo = courseFacade.findCourse(course.getId(), member2.getUuid());
//
//        // then
//        // member1은 고스트 정보 존재
//        CourseGhostResponse ghostStats = responseWithGhostInfo.myGhostInfo();
//        assertNotNull(responseWithGhostInfo);
//        assertThat(ghostStats.runningId()).isEqualTo(running.getId());
//        assertThat(ghostStats.runnerUuid()).isEqualTo(member1.getUuid());
//        // member2는 고스트 정보 존재 X
//        assertThat(responseWithoutGhostInfo.myGhostInfo()).isNull();
//    }
//
//    @DisplayName("주변 코스 검색 시")
//    @ParameterizedTest(name = "{0} 기준으로 정렬하면 그에 맞게 정렬되어야 한다.")
//    @EnumSource(value = CourseSortType.class, names = {"DISTANCE", "POPULARITY"})
//    void findCoursesByPosition_properlySorted(CourseSortType sortType) {
//        // given
//        Course courseNear = createCourse("가까운 코스", DEFAULT_LAT, DEFAULT_LNG);
//        Course courseMid = createCourse("중간 코스", DEFAULT_LAT + 0.005, DEFAULT_LNG + 0.005);
//        Course courseFar = createCourse("먼 코스", defaultMember, DEFAULT_LAT + 0.01, DEFAULT_LNG + 0.01);
//
//        // 인기순: 러너가 많은 코스, 보통 코스, 적은 코스
//        Course coursePopular = createCourse("인기 코스", DEFAULT_LAT + 0.015, DEFAULT_LNG + 0.015);
//        Course courseNormal = createCourse("보통 코스", defaultMember,  DEFAULT_LAT + 0.02, DEFAULT_LNG + 0.02);
//        Course courseUnpopular = createCourse("비인기 코스", DEFAULT_LAT + 0.025, DEFAULT_LNG + 0.025);
//
//        // 최신순: 오래된 코스, 중간 코스, 최신 코스 (ID 오름차순으로 생성)
//        courseRepository.saveAll(List.of(courseNear, courseMid, courseFar, coursePopular, courseNormal, courseUnpopular));
//
//        // 인기순 정렬을 위한 더미 데이터 생성
//        List<Member> memberPool = IntStream.range(0, 10).boxed()
//                .map(i -> createMember("회원" + i))
//                .toList();
//        memberRepository.saveAll(memberPool);
//        saveDummyRunsToCourse(coursePopular, 10, memberPool); // 인기 코스: 10명
//        saveDummyRunsToCourse(courseNormal, 5, memberPool); // 보통 코스: 5명
//        saveDummyRunsToCourse(courseUnpopular, 3, memberPool); // 비인기 코스: 1명
//        saveDummyRunsToCourse(courseNear, 2, memberPool);
//        saveDummyRunsToCourse(courseMid, 1, memberPool);
//        saveDummyRunsToCourse(courseFar, 0, memberPool);
//
//        // when
//        List<CourseMapResponse> actualCourses = courseFacade
//                .findCoursesByPosition(DEFAULT_LAT, DEFAULT_LNG, 19999, sortType, null, defaultMember.getUuid());
//
//        // then
//        List<String> actualCourseNames = actualCourses.stream()
//                .map(CourseMapResponse::name)
//                .toList();
//
//        // 정렬 기준(sortType)에 따라 기대되는 이름 순서를 정의하고 실제 결과와 비교
//        switch (sortType) {
//            case DISTANCE -> {
//                List<String> expectedOrder = List.of("가까운 코스", "중간 코스", "먼 코스", "인기 코스", "보통 코스", "비인기 코스");
//                assertThat(actualCourseNames).containsSequence(expectedOrder);
//            }
//            case POPULARITY -> {
//                List<String> expectedOrder = List.of("인기 코스", "보통 코스", "비인기 코스", "가까운 코스", "중간 코스", "먼 코스");
//                assertThat(actualCourseNames).containsSequence(expectedOrder);
//            }
//        }
//    }

    @DisplayName("주변 코스를 검색하면, 각 코스별 상위 최대 4명의 러너 정보가 함께 조회된다.")
    @Test
    void findCoursesByPositionCached() {
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
                .findCoursesByPositionCached(DEFAULT_LAT, DEFAULT_LNG, 1000, CourseSortType.DISTANCE, null, defaultMember.getUuid());

        // then
        assertThat(courses.size()).isEqualTo(3);
        // 코스별 상위 러너 정보 검증
        assertThat(courses).extracting("runnersCount")
                .containsExactlyInAnyOrder(10L, 3L, 0L);
        List<RunnerProfile> memberPoolRecords = memberPool.stream()
                .map(member -> new RunnerProfile(member.getUuid(), member.getProfilePictureUrl()))
                .toList();
        assertThat(courses).extracting("runners")
                .containsExactlyInAnyOrder(
                        memberPoolRecords.stream().limit(4).toList(),
                        memberPoolRecords.stream().limit(3).toList(),
                        List.of());
    }

//    @DisplayName("주변 코스 검색 결과 본인과 타인의 코스가 섞여있을 때, 소유권에 따른 개수 제한 정책을 올바르게 적용한다.")
//    @ParameterizedTest(name = "[{index}] 내 코스 {0}개, 타인 코스 {1}개일 때 -> 내 코스 {2}개, 타인 코스 {3}개 반환")
//    @MethodSource("provideCourseCountsForLimiting")
//    void findCoursesByPositionCached_limitCount(int myCourseCount, int otherCourseCount, int expectedMyCourseCount, int expectedOtherCourseCount) {
//        // given
//        Member viewer = createMember("아이유");
//        memberRepository.save(viewer);
//
//        // 본인 소유 코스 생성
//        List<Course> myCourses = IntStream.range(0, myCourseCount)
//                .mapToObj(i -> createCourse("내 코스 " + i, viewer, DEFAULT_LAT + i * 0.0001, DEFAULT_LNG))
//                .toList();
//        courseRepository.saveAll(myCourses);
//
//        // 타인 소유 코스 생성
//        List<Course> otherCourses = IntStream.range(0, otherCourseCount)
//                .mapToObj(i -> {
//                    Member owner = saveMember(createMember("회원 " + i));
//                    return createCourse("타인 코스 " + i, owner, DEFAULT_LAT - i * 0.0001, DEFAULT_LNG);
//                })
//                .toList();
//        courseRepository.saveAll(otherCourses);
//
//        // when
//        List<CourseMapResponse> courses = courseFacade.findCoursesByPositionCached(DEFAULT_LAT, DEFAULT_LNG,
//                5000, CourseSortType.DISTANCE, null, viewer.getUuid());
//
//        // then
//        long actualMyCourses = courses.stream()
//                .filter(c -> c.ownerUuid().equals(viewer.getUuid()))
//                .count();
//
//        long actualOtherCourses = courses.stream()
//                .filter(c -> !c.ownerUuid().equals(viewer.getUuid()))
//                .count();
//
//        assertThat(actualMyCourses).isEqualTo(expectedMyCourseCount);
//        assertThat(actualOtherCourses).isEqualTo(expectedOtherCourseCount);
//        assertThat(courses.size()).isEqualTo(expectedMyCourseCount + expectedOtherCourseCount);
//    }
//
//    private static Stream<Arguments> provideCourseCountsForLimiting() {
//        return Stream.of(
//                // 내 코스 5개, 타인 코스 5개 이상 -> 내 코스 5개, 타인 코스 5개
//                Arguments.of(8, 12, 5, 5),
//                // 내 코스 5개 미만, 타인 코스 5개 이상 -> 내 코스 모두, 타인 코스 7개
//                Arguments.of(3, 10, 3, 7),
//                // 내 코스 5개 이상, 타인 코스 5개 미만 -> 내 코스 (10 - 타인 코스 개수) 만큼, 타인 코스 모두
//                Arguments.of(15, 3, 7, 3),
//                // 내 코스와 타인 코스 합쳐서 10개 미만 -> 모든 코스 반환
//                Arguments.of(4, 4, 4, 4),
//                // 내 코스만 있는 경우 -> 최대 10개
//                Arguments.of(12, 0, 10, 0),
//                // 타인 코스만 있는 경우 -> 최대 10개
//                Arguments.of(0, 12, 0, 10)
//            );
//    }

    @DisplayName("주변 코스 검색 시 코스를 달린 기록이 존재하는 경우에만 고스트 정보가 포함된다.")
    @Test
    void findCoursesByPositionCached_withRuns() {
        // given
        Course courseRan = createCourse("달린 코스");
        Course courseNotRan = createCourse("안 달린 코스");
        courseRepository.saveAll(List.of(courseRan, courseNotRan));

        Running myRunning = createRunning("나의 기록", courseRan, defaultMember);
        runningRepository.save(myRunning);

        // when
        List<CourseMapResponse> courses = courseFacade
                .findCoursesByPositionCached(DEFAULT_LAT, DEFAULT_LNG, 1000, CourseSortType.DISTANCE, null, defaultMember.getUuid());

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

    @DisplayName("주변 코스 검색 결과, 코스 추천 정책에 따른 개수 필터링을 올바르게 적용한다. ( 본인 코스(P1) > 추천 코스(P2) > 타인 코스(P3) > 더미 코스(P4) 순서 )")
    @ParameterizedTest(name = "[{index}] P1:{0}, P2:{1}, P3:{2}, P4:{3} -> 최종:{5} (P1:{6}, P2:{7}, P3:{8}, P4:{9})")
    @MethodSource("provideCourseCountsForLimitFiltering")
    void findCoursesByPositionCached_limitFiltering(
            int userCnt, int recommendedCnt, int otherCnt, int dummyCnt, int limit,
            int expectedFinalCnt, int expectedUserCnt, int expectedRecommendedCnt, int expectedOtherCnt, int expectedDummyCnt
    ) {
        // given
        Member viewer = createMember("뷰어");
        memberRepository.save(viewer);
        Member otherOwner = saveMember(createMember("타인"));

        List<Course> courses = new ArrayList<>();
        int courseIndex = 0;

        // 본인 코스, 추천 코스, 타인 코스, 더미 코스 생성
        int idx = 0;
        for (int i = 0; i < userCnt; i++) {
            courses.add(createCourse("P1" + courseIndex++, viewer, DEFAULT_LAT + (idx++) * 0.0001, DEFAULT_LNG));
        }
        for (int i = 0; i < recommendedCnt; i++) {
            Course course = createCourse("P2" + courseIndex++, otherOwner, DEFAULT_LAT + (idx++) * 0.0001, DEFAULT_LNG);
            setCourseSourceForTest(course, CourseSource.RECOMMENDED);
            courses.add(course);
        }
        for (int i = 0; i < otherCnt; i++) {
            courses.add(createCourse("P3" + courseIndex++, otherOwner, DEFAULT_LAT + (idx++) * 0.0001, DEFAULT_LNG));
        }
        for (int i = 0; i < dummyCnt; i++) {
            Course course = createCourse("P4" + courseIndex++, null, DEFAULT_LAT + (idx++) * 0.0001, DEFAULT_LNG);
            setCourseSourceForTest(course, CourseSource.OFFICIAL);
            courses.add(course);
        }

        courseRepository.saveAll(courses);

        // when
        List<CourseMapResponse> result = courseFacade.findCoursesByPositionCached(DEFAULT_LAT, DEFAULT_LNG,
                5000, CourseSortType.DISTANCE, null, viewer.getUuid());

        // then
        long actualUserCount = result.stream()
                .filter(c -> c.name().startsWith("P1"))
                .count();
        long actualRecommendedCount = result.stream()
                .filter(c -> c.name().startsWith("P2"))
                .count();
        long actualOtherCount = result.stream()
                .filter(c -> c.name().startsWith("P3"))
                .count();
        long actualDummyCount = result.stream()
                .filter(c -> c.name().startsWith("P4"))
                .count();

        assertThat(result).hasSize(expectedFinalCnt);
        assertThat(actualUserCount).isEqualTo(expectedUserCnt);
        assertThat(actualRecommendedCount).isEqualTo(expectedRecommendedCnt);
        assertThat(actualOtherCount).isEqualTo(expectedOtherCnt);
        assertThat(actualDummyCount).isEqualTo(expectedDummyCnt);

        // 순서 유지 여부 검증
        if (result.size() > 1) {
            List<Long> resultIds = result.stream().map(CourseMapResponse::id).toList();
            List<Long> originalIds = courses.stream().map(Course::getId).toList();

            int previousIndex = -1;
            for (Long resultId : resultIds) {
                int currentIndex = originalIds.indexOf(resultId);
                assertThat(currentIndex).isGreaterThan(previousIndex);
                previousIndex = currentIndex;
            }
        }
    }

    private static Stream<Arguments> provideCourseCountsForLimitFiltering() {
        // 인자 순서: { 내 코스 개수 (P1), 추천 코스 개수 (P2), 타인 코스 개수 (P3), 더미 코스 개수 (P4), limit,
        //            예상 최종 개수, 예상 P1, 예상 P2, 예상 P3, 예상 P4 }
        return Stream.of(
                Arguments.of(10, 10, 10, 10, 10,
                        10, 5, 2, 3, 0), // 코스가 풍부한 경우
                Arguments.of(3, 10, 10, 10, 10,
                        10, 3, 2, 5, 0), // P1이 부족한 경우
                Arguments.of(3, 1, 10, 10, 10,
                        10, 3, 1, 6, 0), // P1, P2가 부족한 경우
                Arguments.of(3, 1, 2, 10, 10,
                        10, 3, 1, 2, 4), // P1, P2, P3가 부족한 걸 P4가 메꾸는 경우
                Arguments.of(3, 1, 1, 1, 10,
                        6, 3, 1, 1, 1), // 전체 코스가 부족한 경우
                Arguments.of(0, 5, 0, 0, 10,
                        2, 0, 2, 0, 0), // 추천 코스만 존재하는 경우
//                Arguments.of(10, 10, 10, 10, 5,
//                        5, 2, 1, 2, 0), // Limit이 5인 경우
//                Arguments.of(10, 10, 10, 10, 7,
//                        7, 3, 1, 3, 0), // Limit이 7인 경우
//                Arguments.of(10, 10, 10, 10,
//                        1, 1, 0, 0, 1, 0), // Limit이 1인 경우
                Arguments.of(0, 0, 0, 0, 10,
                        0, 0, 0, 0, 0) // 모든 코스가 없는 경우
        );
    }

    @DisplayName("주변 코스 검색 시")
    @ParameterizedTest(name = "{0} 기준으로 정렬하면 그에 맞게 정렬되어야 한다.")
    @EnumSource(value = CourseSortType.class, names = {"DISTANCE", "POPULARITY"})
    void findCoursesByPositionCached_properlySorted(CourseSortType sortType) {
        Member member = createMember("회원");
        memberRepository.save(member);
        // given
        Course courseNear = createCourse("가까운 코스", DEFAULT_LAT, DEFAULT_LNG);
        Course courseMid = createCourse("중간 코스", DEFAULT_LAT + 0.005, DEFAULT_LNG + 0.005);
        Course courseFar = createCourse("먼 코스", member, DEFAULT_LAT + 0.01, DEFAULT_LNG + 0.01);

        // 인기순: 러너가 많은 코스, 보통 코스, 적은 코스
        Course coursePopular = createCourse("인기 코스", DEFAULT_LAT + 0.015, DEFAULT_LNG + 0.015);
        Course courseNormal = createCourse("보통 코스", member,  DEFAULT_LAT + 0.02, DEFAULT_LNG + 0.02);
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
                .findCoursesByPositionCached(DEFAULT_LAT, DEFAULT_LNG, 19999, sortType, null, defaultMember.getUuid());

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

    @DisplayName("주변 코스 조회 시 일부 캐시만 미스가 발생한 경우, DB에서 다시 조회하여 응답한다.")
    @Test
    void findCoursesByPosition_OnPartialCacheMiss() {
        // given
        var viewer = createMember("아이유");
        memberRepository.save(viewer);
        var memberPool = IntStream.range(0, 5).boxed()
                .map(i -> Member.of("회원" + i, "profile-url-" + i))
                .toList();
        memberRepository.saveAll(memberPool);
        // 코스 저장 - 하나는 캐시, 두 개는 DB에 저장
        var hitCourse = createCourse("캐시된 코스");
        var missedCourse1 = createCourse("DB 저장 코스 1");
        var missedCourse2 = createCourse("DB 저장 코스 2");
        courseRepository.saveAll(List.of(hitCourse, missedCourse1, missedCourse2));
        CourseQueryModel cachedData = new CourseQueryModel(
                hitCourse.getId(),
                "캐시된 코스",
                List.of(new RunnerProfile(viewer.getUuid(), viewer.getProfilePictureUrl())),
                1
        );
        courseCacheRepository.save(cachedData);
        // 러닝 기록 저장 - 캐시된 코스는 1명, DB 코스 1은 3명, DB 코스 2는 5명
        // viewer는 코스 1을 달렸다고 가정
        saveDummyRunsToCourse(hitCourse, 1, List.of(viewer));
        saveDummyRunsToCourse(missedCourse1, 3, memberPool);
        saveDummyRunsToCourse(missedCourse2, 5, memberPool);

        // when
        var courses = courseFacade.findCoursesByPositionCached(DEFAULT_LAT, DEFAULT_LNG, 5000,
                CourseSortType.DISTANCE, null, viewer.getUuid());

        // then
        assertThat(courses).hasSize(3);
        // 캐시 히트된 코스 검증
        var hitResponse = courses.stream().filter(c -> c.id().equals(hitCourse.getId())).findFirst()
                .orElseThrow();
        assertThat(hitResponse.runnersCount()).isEqualTo(1L);
        assertThat(hitResponse.runners()).hasSize(1)
                .extracting("uuid")
                .containsExactly(viewer.getUuid());
        assertThat(hitResponse.myGhostInfo().runnerUuid()).isEqualTo(viewer.getUuid());
        // 캐시 미스된 코스들 검증
        var missedResponse1 = courses.stream().filter(c -> c.id().equals(missedCourse1.getId())).findFirst()
                .orElseThrow();
        assertThat(missedResponse1.runnersCount()).isEqualTo(3L);
        assertThat(missedResponse1.runners()).hasSize(3)
                .extracting("uuid")
                .containsExactlyElementsOf(
                        memberPool.stream().limit(3).map(Member::getUuid).toList()
                );
        assertThat(missedResponse1.myGhostInfo()).isNull();

        var missedResponse2 = courses.stream().filter(c -> c.id().equals(missedCourse2.getId())).findFirst()
                .orElseThrow();
        assertThat(missedResponse2.runnersCount()).isEqualTo(5L);
        assertThat(missedResponse2.runners()).hasSize(4)
                .extracting("uuid")
                .containsExactlyElementsOf(
                        memberPool.stream().limit(4).map(Member::getUuid).toList()
                );
        assertThat(missedResponse2.myGhostInfo()).isNull();

        // 캐시 리포지토리에는 캐시된 코스 존재
        assertThat(courseCacheRepository.findById(hitCourse.getId()).id()).isNotNull();
        assertThat(courseCacheRepository.findById(missedCourse1.getId())).isNotNull();
        assertThat(courseCacheRepository.findById(missedCourse2.getId())).isNotNull();
    }

    // --- Helper Methods ---
    private Course createCourse(String name) {
        return createCourse(name, defaultMember, DEFAULT_LAT, DEFAULT_LNG, CourseProfile.of(100d, 10d, 10d, -10d));
    }

    private Course createCourse(String name, double lat, double lng) {
        return createCourse(name, defaultMember, lat, lng, CourseProfile.of(100d, 10d, 10d, -10d));
    }

    private Course createCourse(String name, Member member) {
        return createCourse(name, member, DEFAULT_LAT, DEFAULT_LNG, CourseProfile.of(100d, 10d, 10d, -10d));
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

    // 코스에 더미 러닝기록을 n개 저장한다 (러닝 성적은 i = 0->n으로 갈수록 낮아진다)
    private void saveDummyRunsToCourse(Course course, int runsToSave, List<Member> memberPool) {
        long initialRunDuration = 3600L;
        for (int i = 0; i < runsToSave; i++) {
            runningRepository.save(
                    createRunning("러닝" + i, course, memberPool.get(i %  memberPool.size()), initialRunDuration + 60L * i)
            );
        }
    }

    // Reflection으로 Course 엔티티의 CourseSource 필드를 설정한다
    private Course setCourseSourceForTest(Course course, CourseSource source) {
        if (course == null) return null;
        try {
            // Course 클래스에서 courseSource 필드를 찾음
            Field field = Course.class.getDeclaredField("source");
            field.setAccessible(true); // private 필드에 접근 허용
            field.set(course, source); // 필드 값 변경
            field.setAccessible(false); // 접근 권한 되돌리기
            return course;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Course 엔티티에 courseSource 필드가 없거나 접근 오류 발생 시
            throw new RuntimeException("CourseSource 필드를 Reflection으로 설정하는 데 실패했습니다.", e);
        }
    }

}