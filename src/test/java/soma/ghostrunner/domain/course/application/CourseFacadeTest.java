package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.dao.RegionRepository;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseDataUrls;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.domain.Region;
import soma.ghostrunner.domain.course.dto.response.CourseMapResponse;
import soma.ghostrunner.domain.course.enums.CourseSortType;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;
import soma.ghostrunner.global.config.CacheType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class CourseFacadeTest extends IntegrationTestSupport {

    private final double DEFAULT_LAT = 37, DEFAULT_LNG = 129;

    /**
     * 셀 버킷 캐시를 경유하지 않는 반경 (설계 §3-8의 광역 가드 3,000m 초과).
     * 선별 정책만 보려는 테스트가 캐시 적재/히트에 흔들리지 않도록 직행 경로를 쓴다.
     */
    private static final int UNCACHED_RADIUS_M = 5000;

    @Autowired
    private CourseFacade courseFacade;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RunningRepository runningRepository;

    @Autowired
    private CourseReadModelRepository readModelRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 셀 버킷 캐시가 쓰는 Redis 키 전부를 훑는 패턴 (CourseReadModelReaderTest와 동일) */
    private static final String CELL_KEY_PATTERN = CacheType.Names.COURSE_CELLS + "*";

    private Member defaultMember;

    @BeforeEach
    void setUp() {
        defaultMember = memberRepository.save(Member.of("기본 회원", "test-url"));
        // 셀 버킷 캐시는 Redis에 남아 테스트 간 순서 의존을 만든다 — 캐시 관련 테스트가 여럿이므로 진입 시점에 공통 정리한다.
        clearCellCache();
    }

    @DisplayName("주변 코스 검색 시 코스를 달린 기록이 존재하는 경우에만 고스트 정보가 포함된다.")
    @Test
    void findCoursesByPosition_withRuns() {
        // given
        Course courseRan = createCourse("달린 코스");
        Course courseNotRan = createCourse("안 달린 코스");
        courseRepository.saveAll(List.of(courseRan, courseNotRan));
        saveReadModel(courseRan);
        saveReadModel(courseNotRan);

        Running myRunning = createRunning("나의 기록", courseRan, defaultMember);
        runningRepository.save(myRunning);

        // when
        List<CourseMapResponse> courses = courseFacade.findCoursesByPosition(
                DEFAULT_LAT, DEFAULT_LNG, 1000, CourseSortType.DISTANCE, null, null, defaultMember.getUuid());

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
    void findCoursesByPosition_limitFiltering(
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
        courses.forEach(this::saveReadModel);

        // when
        List<CourseMapResponse> result = courseFacade.findCoursesByPosition(DEFAULT_LAT, DEFAULT_LNG,
                UNCACHED_RADIUS_M, CourseSortType.DISTANCE, null, null, viewer.getUuid());

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

        // 선별은 후보 순서를 흐트러뜨리지 않는다 — 리드모델 조회가 시작점 위도 오름차순으로 주고,
        // 픽스처의 코스도 그 순서로 만들었으므로 결과는 생성 순서의 부분 수열이어야 한다.
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
                Arguments.of(0, 0, 0, 0, 10,
                        0, 0, 0, 0, 0) // 모든 코스가 없는 경우
        );
    }

    /**
     * regionId는 하위호환으로 수용만 하고 조회에 사용하지 않는다 (설계 §3-10).
     *
     * regionId를 실은 요청이 대표좌표 기준 결과를 받게 되면, 사용자가 그 지역 어디에 서 있든 같은 답이 돌아온다.
     * 예외도 로그도 없는 침묵 오답이라 관측되지 않는다. 결과가 오직 요청 좌표로만 결정된다는 것을 고정한다 —
     * region 대표좌표를 요청 좌표에서 멀리 떼어 두어, 두 경로가 갈리면 반드시 드러나게 한다.
     */
    @DisplayName("regionId를 실어 보내도 좌표 기반 경로와 동일한 결과를 반환한다")
    @Test
    void findCoursesByPosition_withRegionId_returnsSameResultAsCoordinatePath() {
        // given : 요청 좌표에서 멀리 떨어진 대표좌표를 가진 지역과, 각 좌표 위의 코스
        Region farRegion = regionRepository.save(
                Region.of("서울특별시 강남구 역삼동", DEFAULT_LAT + 0.5, DEFAULT_LNG + 0.5));
        savePublicCourseWithReadModel("요청 좌표 코스", DEFAULT_LAT, DEFAULT_LNG);
        savePublicCourseWithReadModel("옆 동네 코스", DEFAULT_LAT + 0.5, DEFAULT_LNG + 0.5);

        // when : 같은 좌표·반경으로 regionId만 실어 보낸 요청과 실지 않은 요청
        List<CourseMapResponse> withRegionId = courseFacade.findCoursesByPosition(
                DEFAULT_LAT, DEFAULT_LNG, 2000, CourseSortType.DISTANCE, null,
                farRegion.getId(), defaultMember.getUuid());
        List<CourseMapResponse> withoutRegionId = courseFacade.findCoursesByPosition(
                DEFAULT_LAT, DEFAULT_LNG, 2000, CourseSortType.DISTANCE, null,
                null, defaultMember.getUuid());

        // then : 결과는 요청 좌표로만 결정된다
        assertThat(withRegionId).extracting(CourseMapResponse::name)
                .containsExactlyElementsOf(
                        withoutRegionId.stream().map(CourseMapResponse::name).toList());
        assertThat(withRegionId).extracting(CourseMapResponse::name)
                .containsExactly("요청 좌표 코스");
    }

    private void clearCellCache() {
        Set<String> keys = redisTemplate.keys(CELL_KEY_PATTERN);
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
        saveReadModel(course);
    }

    /** 지도 조회(리드모델 경로)의 전제를 만든다 — 저장된 코스에 공개 리드모델을 붙인다. */
    private void saveReadModel(Course course) {
        CourseReadModel readModel = CourseReadModel.create(course);
        readModel.makePublic();
        readModelRepository.save(readModel);
    }

    // --- Helper Methods ---
    private Course createCourse(String name) {
        return createCourse(name, defaultMember, DEFAULT_LAT, DEFAULT_LNG);
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

    private Running createRunning(String runningName, Course course, Member member) {
        return Running.of(
                runningName, RunningMode.SOLO, null,
                createRunningRecord(), 1750729987181L,
                true, false,
                "Raw Telemetry Mock URL", "Interpolated Mock URL", "screenShot",
                member, course
        );
    }

    @DisplayName("고스트 정렬 필드가 화이트리스트(GhostSortType) 밖이면 조회 전에 거절한다")
    @Test
    void findPublicGhosts_rejectsUnknownSortField() {
        // 임의 프로퍼티가 JPA 정렬로 새어 들어가지 않아야 한다 — Reader 호출 전에 막힌다.
        assertThatThrownBy(() -> courseFacade.findPublicGhosts(
                1L, PageRequest.of(0, 10, Sort.by("member.uuid"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Member createMember(String nickname) {
        return Member.of(nickname, "picture-url");
    }

    private Member saveMember(Member member) {
        return memberRepository.save(member);
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
