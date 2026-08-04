package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseCacheRepository;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.dao.RegionRepository;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseDataUrls;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.domain.Region;
import soma.ghostrunner.domain.course.dto.CourseSearchFilterDto;
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
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

    @Autowired
    private CourseReadModelRepository readModelRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** course-map 캐시가 쓰는 Redis 키 전부를 훑는 패턴 (CourseReadModelReaderTest와 동일) */
    private static final String COURSE_MAP_KEY_PATTERN = "course-map*";

    private Member defaultMember;

    @BeforeEach
    void setUp() {
        defaultMember = memberRepository.save(Member.of("기본 회원", "test-url"));
        // course-map 캐시는 Redis에 남아 테스트 간 순서 의존을 만든다 — 캐시 관련 테스트가 여럿이므로 진입 시점에 공통 정리한다.
        clearCourseMapCache();
    }

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
                .map(member -> new RunnerProfile(member.getUuid(), member.getProfilePictureUrl(), null))
                .toList();
        assertThat(courses).extracting("runners")
                .containsExactlyInAnyOrder(
                        memberPoolRecords.stream().limit(4).toList(),
                        memberPoolRecords.stream().limit(3).toList(),
                        List.of());
    }

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
                List.of(new RunnerProfile(viewer.getUuid(), viewer.getProfilePictureUrl(), null)),
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

    /**
     * regionId 캐시 경로의 경계 검증 — 캐시는 "기본 요청"에만 적용된다 (설계 §5-2, §6-6).
     *
     * 비기본 요청(필터·정렬)에까지 regionId 키를 쓰면, 같은 course-map::{regionId} 엔트리에 필터마다
     * 다른 결과가 실려 캐시 값의 결정성이 깨진다. 따라서 regionId가 붙어 있어도 비기본 요청은
     * 캐시를 만들지도, 읽지도 않고 요청 좌표로 직접 조회해야 한다.
     */
    @DisplayName("regionId가 첨부돼도 비기본 요청이면 캐시 경로를 타지 않는다 - 요청 좌표 기준으로 조회되고 course-map 키도 생기지 않는다")
    @Test
    void findCoursesByPosition_withRegionIdAndNonDefaultRequest_bypassesCache() {
        // given : 요청 좌표에서 멀리 떨어진 지역(대표좌표)과 그 동네 코스, 그리고 요청 좌표 위의 코스
        Region farRegion = regionRepository.save(
                Region.of("서울특별시 강남구 역삼동", DEFAULT_LAT + 0.5, DEFAULT_LNG + 0.5));
        savePublicCourseWithReadModel("옆 동네 코스", DEFAULT_LAT + 0.5, DEFAULT_LNG + 0.5);
        savePublicCourseWithReadModel("요청 좌표 코스", DEFAULT_LAT, DEFAULT_LNG);

        // when : regionId를 실었지만 필터가 붙은 비기본 요청
        CourseSearchFilterDto nonDefaultRequest = CourseSearchFilterDto.of(1000, null, null, null, null);
        List<CourseMapResponse> courses = courseFacade.findCoursesByPosition(
                DEFAULT_LAT, DEFAULT_LNG, 2000, CourseSortType.DISTANCE, nonDefaultRequest,
                farRegion.getId(), defaultMember.getUuid());

        // then : 지역 대표좌표가 아니라 요청 좌표 기준 결과가 나온다
        assertThat(courses).extracting(CourseMapResponse::name).containsExactly("요청 좌표 코스");

        // 캐시 키 공간도 오염되지 않는다 (비캐시 경로이므로 적재 자체가 없어야 한다)
        assertThat(redisTemplate.keys(COURSE_MAP_KEY_PATTERN)).isEmpty();
    }

    /**
     * 미발급 regionId는 홈 화면을 막지 않는다 (설계 §5-1 "실패해도 폴백으로 코스 조회 가능").
     *
     * dev 환경은 ddl-auto: create라 배포마다 region 테이블이 비워지는 반면 FE는 regionId를 로컬에 보관한다.
     * 즉 "발급된 적 없는 regionId"는 배포마다 확정 재현되며, 이때 404로 응답하면 QA 기기 전원의 홈이 백지가 된다.
     * 좌표 폴백이라는 완전한 복구 경로가 이미 있으므로 조용히 강등한다.
     */
    @DisplayName("발급된 적 없는 regionId로 기본 요청이 와도 예외 없이 요청 좌표 기준 결과를 반환하고 캐시도 오염되지 않는다")
    @Test
    void findCoursesByPosition_withUnknownRegionId_fallsBackToRequestCoordinate() {
        // given : region 테이블에 없는 regionId와, 요청 좌표 위의 코스
        Long unknownRegionId = 999_999L;
        savePublicCourseWithReadModel("요청 좌표 코스", DEFAULT_LAT, DEFAULT_LNG);

        // when : FE가 보관하던 옛 regionId를 실어 보낸 기본 요청
        List<CourseMapResponse> courses = courseFacade.findCoursesByPosition(
                DEFAULT_LAT, DEFAULT_LNG, 2000, CourseSortType.DISTANCE, null,
                unknownRegionId, defaultMember.getUuid());

        // then : 홈이 죽지 않는다 — 요청 좌표 기준 결과가 그대로 내려온다
        assertThat(courses).extracting(CourseMapResponse::name).containsExactly("요청 좌표 코스");

        // 없는 지역의 좌표 기반 결과가 course-map::{regionId}에 실리면 안 된다
        assertThat(redisTemplate.keys(COURSE_MAP_KEY_PATTERN)).isEmpty();
    }

    /**
     * 지역 캐시 값은 대표좌표 기준 고정 2km다 (설계 §4). 광역 줌 요청에 regionId가 실려 오면
     * 서버가 2km 결과를 조용히 돌려주게 되는데, 이는 예외도 로그도 없는 침묵 오답이다.
     * "지도 중심 ≈ 사용자 GPS"라는 FE 규율에 정확성을 의존하지 않도록 서버가 스스로 캐시 경로를 포기한다.
     */
    @DisplayName("regionId가 붙은 기본 요청이라도 광역 반경(10km)이면 캐시 경로를 타지 않고 요청 좌표·반경 기준으로 조회한다")
    @Test
    void findCoursesByPosition_withRegionIdAndWideRadius_bypassesCache() {
        // given : 요청 좌표에서 멀리 떨어진 지역(대표좌표)과 그 동네 코스, 요청 좌표 위의 코스,
        //         그리고 고정 2km 밖이지만 요청 반경 10km 안에 있는 코스
        Region farRegion = regionRepository.save(
                Region.of("서울특별시 강남구 역삼동", DEFAULT_LAT + 0.5, DEFAULT_LNG + 0.5));
        savePublicCourseWithReadModel("옆 동네 코스", DEFAULT_LAT + 0.5, DEFAULT_LNG + 0.5);
        savePublicCourseWithReadModel("요청 좌표 코스", DEFAULT_LAT, DEFAULT_LNG);
        savePublicCourseWithReadModel("광역 반경 코스", DEFAULT_LAT + 0.045, DEFAULT_LNG);

        // when : regionId를 실었지만 뷰포트가 광역(10km)인 기본 요청
        List<CourseMapResponse> courses = courseFacade.findCoursesByPosition(
                DEFAULT_LAT, DEFAULT_LNG, 10000, CourseSortType.DISTANCE, null,
                farRegion.getId(), defaultMember.getUuid());

        // then : 대표좌표 기준 2km가 아니라 요청 좌표 기준 10km 결과가 나온다
        assertThat(courses).extracting(CourseMapResponse::name)
                .containsExactlyInAnyOrder("요청 좌표 코스", "광역 반경 코스");

        // 캐시 키 공간도 오염되지 않는다 (캐시 경로 자체를 타지 않으므로 적재가 없어야 한다)
        assertThat(redisTemplate.keys(COURSE_MAP_KEY_PATTERN)).isEmpty();
    }

    private void clearCourseMapCache() {
        Set<String> keys = redisTemplate.keys(COURSE_MAP_KEY_PATTERN);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private void savePublicCourseWithReadModel(String name, double lat, double lng) {
        Course course = courseRepository.save(Course.of(
                defaultMember, name,
                CourseProfile.of(5.0, 10.0, 100.0, 50.0),
                Coordinate.of(lat, lng),
                CourseSource.USER, true,
                CourseDataUrls.of("https://example.com/route.json",
                        "https://example.com/checkpoints.json",
                        "https://example.com/thumb.jpg")));
        CourseReadModel readModel = CourseReadModel.create(course);
        readModel.makePublic();
        readModelRepository.save(readModel);
    }

    // --- Helper Methods ---
    private Course createCourse(String name) {
        return createCourse(name, defaultMember, DEFAULT_LAT, DEFAULT_LNG);
    }

    private Course createCourse(String name, double lat, double lng) {
        return createCourse(name, defaultMember, lat, lng);
    }

    private Course createCourse(String name, Member member, double lat, double lng) {
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
