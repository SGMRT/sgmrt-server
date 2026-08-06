package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import soma.ghostrunner.DatabaseCleanserExtension;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.BoundingBox;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseDataUrls;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.domain.GeoCell;
import soma.ghostrunner.domain.course.dto.query.CourseMapDto;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.global.config.CacheType;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 셀 버킷(geohash p6) 캐시 경로의 조회 계약 검증.
 *
 * <p>설계 문서: docs/design/course-cell-bucket-cache-design.md §3-8, §6 테스트 9~13</p>
 *
 * <p>이 경로가 지켜야 할 불변식은 넷이다.</p>
 * <ol>
 *   <li><b>부분 채움</b> — 히트한 셀은 다시 읽히지도 덮이지도 않고, 미스 셀만 채워진다. 두 몫을 합쳐도 중복이 없다.
 *       캐시 단위가 요청자가 아니라 <b>코스의 시작점 셀</b>이기 때문에 성립하는 성질이다.</li>
 *   <li><b>네거티브 캐싱</b> — 코스가 없는 셀도 빈 배열로 적재된다. 적재하지 않으면 그 셀은 TTL 내내 영구 미스가 되어
 *       외곽 요청이 매번 채움 쿼리를 돈다.</li>
 *   <li><b>적재 스킵</b> — 채움 쿼리가 fill-limit에 걸리면 응답은 내되 <b>적재는 전체 스킵</b>한다. 잘린 결과는
 *       공간적으로 편향돼 있어, 그 값이 TTL 동안 캐시에 각인되는 것만은 막아야 한다.</li>
 *   <li><b>강등</b> — 캐시를 쓸 수 없는 요청은 전부 직행 한 곳으로 수렴하고, 직행 결과는 캐시 경로와 <b>같은 원 필터</b>를
 *       거친다. 그래야 같은 요청의 응답이 Redis 상태에 따라 흔들리지 않는다.</li>
 * </ol>
 *
 * <p>Redis 장애 강등은 mock이 필요해 {@code CourseCellCacheDegradeTest}가 담당한다 — 여기서 중복하지 않는다.</p>
 */
@DisplayName("CourseReadModelReader 통합 테스트 - 셀 버킷 캐시")
@ExtendWith(DatabaseCleanserExtension.class)
class CourseReadModelReaderTest extends IntegrationTestSupport {

    @Autowired CourseReadModelReader reader;
    @Autowired CourseReadModelRepository readModelRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired StringRedisTemplate stringRedisTemplate;

    /** 이 캐시가 쓰는 Redis 키 전부를 훑는 패턴. DatabaseCleanserExtension은 Redis를 건드리지 않는다. */
    private static final String CELL_KEY_PATTERN = CacheType.Names.COURSE_CELLS + "*";
    private static final String CELL_KEY_PREFIX = CacheType.Names.COURSE_CELLS + "::";

    private static final double SEOUL_LAT = 37.5665;
    private static final double SEOUL_LNG = 126.9780;

    /** 홈 기본 뷰에 해당하는 반경. 캐시를 타는 정상 요청이다. */
    private static final int MAP_RADIUS_M = 1000;

    /** 광역 가드 (설계 §3-8). 이 값을 넘는 요청은 캐시를 경유하지 않는다. */
    private static final int MAX_CACHEABLE_RADIUS_M = 3000;

    /**
     * 커버링 셀 수 상한(128)을 넘기는 극단 좌표. cos(lat)이 0에 수렴해 경도 범위가 폭발한다
     * (설계 §10 V4 — lat 89.9 / r=3000 → 31,020셀). lat/lng에 검증 애노테이션이 없어 실제로 도달 가능한 입력이다.
     */
    private static final double EXTREME_LAT = 89.9;
    private static final double EXTREME_LNG = 0.0;

    // 이웃한 두 셀 — 셀 A는 서울이 속한 셀, 셀 B는 그 동쪽 셀이다. 좌표는 셀 경계에서 역산해 셀 소속을 확정한다.
    private static final GeoCell CELL_A = GeoCell.of(SEOUL_LAT, SEOUL_LNG);
    private static final BoundingBox CELL_A_BOUNDS = CELL_A.bounds();
    private static final double CELL_LAT_SPAN = CELL_A_BOUNDS.maxLat() - CELL_A_BOUNDS.minLat();
    private static final double CELL_LNG_SPAN = CELL_A_BOUNDS.maxLng() - CELL_A_BOUNDS.minLng();
    private static final double CELL_A_LAT = CELL_A_BOUNDS.minLat() + CELL_LAT_SPAN / 2;
    private static final double CELL_A_LNG = CELL_A_BOUNDS.minLng() + CELL_LNG_SPAN / 2;
    private static final double CELL_B_LAT = CELL_A_LAT;
    private static final double CELL_B_LNG = CELL_A_LNG + CELL_LNG_SPAN;
    private static final GeoCell CELL_B = GeoCell.of(CELL_B_LAT, CELL_B_LNG);

    /** 두 셀의 경계. 여기서 반경 1km면 셀 A·B가 모두 커버링에 들어가고 두 셀의 코스가 모두 원 안이다(각 약 483m). */
    private static final double BORDER_LAT = CELL_A_LAT;
    private static final double BORDER_LNG = CELL_A_LNG + CELL_LNG_SPAN / 2;

    /**
     * 셀 하나만 덮는 반경. 셀 크기가 위도 610m · 경도 967m라, 셀 중심에서 200m 박스는 그 셀을 벗어나지 않는다.
     * 특정 셀만 미리 적재해 "부분 히트" 상황을 만드는 데 쓴다.
     */
    private static final int SINGLE_CELL_RADIUS_M = 200;

    private static final String COURSE_IN_CELL_A = "셀A 코스";
    private static final String COURSE_IN_CELL_B = "셀B 코스";
    private static final String COURSE_ADDED_AFTER_FILL = "적재 후 셀A에 추가된 코스";
    private static final String COURSE_AT_CENTER = "중심 코스";
    private static final String COURSE_NEAR_CENTER = "이웃 코스";
    private static final String COURSE_AT_1KM_BOX_CORNER = "1km 박스 모서리 코스";
    private static final String COURSE_AT_3KM_BOX_CORNER = "3km 박스 모서리 코스";
    private static final String COURSE_AT_EXTREME_CENTER = "극지 중심 코스";
    private static final String COURSE_AT_EXTREME_CORNER = "극지 박스 모서리 코스";

    private MapFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new MapFixture(memberRepository, courseRepository, readModelRepository, stringRedisTemplate);
        fixture.clearCellKeys();
    }

    @DisplayName("히트한 셀은 갱신되지 않고 미스 셀만 새로 적재되며, 두 몫을 합친 결과에 중복이 없다")
    @Test
    void partialFill_KeepsHitCellIntactAndFillsOnlyMissedCells() {
        // given : 이웃한 두 셀에 코스를 하나씩 두고, 셀 A만 미리 적재한다
        //         (적재 요청은 중심 200m, 재사용 요청은 경계 1km — 캐시 값이 요청자와 무관한 "셀의 내용물"이라 성립한다)
        fixture.savePublicCourse(COURSE_IN_CELL_A, CELL_A_LAT, CELL_A_LNG);
        fixture.savePublicCourse(COURSE_IN_CELL_B, CELL_B_LAT, CELL_B_LNG);
        reader.findCoursesForMap(CELL_A_LAT, CELL_A_LNG, SINGLE_CELL_RADIUS_M);

        assertThat(fixture.cellKeys()).containsExactly(fixture.cellKey(CELL_A));   // 사전 조건 : 셀 A만 적재된 상태
        String cellASnapshot = fixture.rawValue(CELL_A);

        // given : 적재 이후 셀 A 영역에 코스가 하나 더 생긴다 — 히트 셀이 갱신되면 이 코스가 응답에 새어 나온다
        fixture.savePublicCourse(COURSE_ADDED_AFTER_FILL, CELL_A_LAT + CELL_LAT_SPAN / 4, CELL_A_LNG);

        // when : 두 셀을 모두 덮는 조회 (셀 A 히트 + 셀 B 미스)
        List<CourseMapDto> courses = reader.findCoursesForMap(BORDER_LAT, BORDER_LNG, MAP_RADIUS_M);

        // then : 히트 셀은 적재 시점 그대로 쓰이고(새 코스 미반영), 미스 셀만 DB에서 채워진다
        assertThat(names(courses)).containsExactlyInAnyOrder(COURSE_IN_CELL_A, COURSE_IN_CELL_B);

        // then : 채움 쿼리 박스에 히트 셀이 딸려와도 그 행은 버려진다 — 캐시분과 채움분이 겹치지 않는 근거
        assertThat(courses).extracting(CourseMapDto::courseId).doesNotHaveDuplicates();

        // then : 새로 생긴 키는 미스 셀 쪽이고, 히트 셀의 값은 손대지 않는다
        assertThat(fixture.cellKeys()).contains(fixture.cellKey(CELL_A), fixture.cellKey(CELL_B));
        assertThat(fixture.rawValue(CELL_A)).isEqualTo(cellASnapshot);
    }

    @DisplayName("코스가 없는 셀도 빈 배열로 적재되어, 재조회는 미스 없이 캐시만으로 끝난다")
    @Test
    void negativeCaching_FillsEmptyCellsSoRefetchNeverTouchesDb() {
        // given : 코스가 하나도 없는 영역
        List<GeoCell> covering = GeoCell.covering(SEOUL_LAT, SEOUL_LNG, MAP_RADIUS_M);

        // when : 첫 조회 — 전 셀 미스
        List<CourseMapDto> first = reader.findCoursesForMap(SEOUL_LAT, SEOUL_LNG, MAP_RADIUS_M);

        // then : 커버링 셀 전부가 빈 배열로 남는다. 적재하지 않으면 이 셀들은 TTL 내내 영구 미스다
        assertThat(first).isEmpty();
        assertThat(fixture.cellKeys())
                .containsExactlyInAnyOrderElementsOf(covering.stream().map(fixture::cellKey).toList());
        assertThat(covering).allSatisfy(cell -> assertThat(fixture.rawValue(cell)).isEqualTo("[]"));

        // when : 적재 뒤 DB에 코스가 생겨도 —
        fixture.savePublicCourse(COURSE_AT_CENTER, SEOUL_LAT, SEOUL_LNG);
        List<CourseMapDto> second = reader.findCoursesForMap(SEOUL_LAT, SEOUL_LNG, MAP_RADIUS_M);

        // then : 두 번째 조회는 전 셀 히트라 DB를 보지 않는다 (새 코스는 이빅트/TTL 전까지 보이지 않는 것이 정상)
        assertThat(second).isEmpty();
    }

    @DisplayName("광역 요청(3001m)과 극단 좌표는 캐시를 만들지 않고 직행 경로와 같은 결과를 낸다")
    @Test
    void degradesToDirectQuery_WhenRadiusIsTooWideOrCoveringIsTooLarge() {
        // given : 두 위치 각각에 '원 안' 코스와 '박스 안 · 원 밖(모서리)' 코스
        int tooWideRadius = MAX_CACHEABLE_RADIUS_M + 1;
        fixture.savePublicCourse(COURSE_AT_CENTER, SEOUL_LAT, SEOUL_LNG);
        fixture.savePublicCourse(COURSE_AT_3KM_BOX_CORNER,
                SEOUL_LAT + latOffset(2900), SEOUL_LNG + lngOffset(2900, SEOUL_LAT));
        fixture.savePublicCourse(COURSE_AT_EXTREME_CENTER, EXTREME_LAT, EXTREME_LNG);
        fixture.savePublicCourse(COURSE_AT_EXTREME_CORNER,
                EXTREME_LAT + latOffset(2900), EXTREME_LNG + lngOffset(2900, EXTREME_LAT));

        // when : 광역 가드로 강등 / 커버링 셀 수 상한으로 강등
        List<CourseMapDto> tooWide = reader.findCoursesForMap(SEOUL_LAT, SEOUL_LNG, tooWideRadius);
        List<CourseMapDto> extreme = reader.findCoursesForMap(EXTREME_LAT, EXTREME_LNG, MAX_CACHEABLE_RADIUS_M);

        // then : 강등 경로도 원 필터를 거친다 — 모서리 코스는 박스 안이지만 중심에서 약 4.1km라 원 밖이다
        assertThat(names(tooWide)).containsExactly(COURSE_AT_CENTER);
        assertThat(names(extreme)).containsExactly(COURSE_AT_EXTREME_CENTER);

        // then : 강등 요청은 캐시를 읽지도 쓰지도 않는다
        assertThat(fixture.cellKeys()).isEmpty();
    }

    @DisplayName("DB 채움 경로와 캐시 히트 경로의 후보가 같고, 박스 모서리(반경 밖) 코스는 양쪽 모두에서 빠진다")
    @Test
    void cachePathAndDirectPath_YieldTheSameCandidates() {
        // given : 후보 50개 이하 픽스처 전제(직행 LIMIT 50). 그 위에서는 캐시 경로(모집단 상한 없음)와 애초에 같을 수 없다.
        fixture.savePublicCourse(COURSE_AT_CENTER, SEOUL_LAT, SEOUL_LNG);
        fixture.savePublicCourse(COURSE_NEAR_CENTER, SEOUL_LAT + latOffset(300), SEOUL_LNG);
        // 반경 1km 박스의 모서리 — dy·dx가 각각 900m라 박스 안이지만 중심에서 1,273m라 원 밖이다
        fixture.savePublicCourse(COURSE_AT_1KM_BOX_CORNER,
                SEOUL_LAT + latOffset(900), SEOUL_LNG + lngOffset(900, SEOUL_LAT));
        // 직행 경로(3,001m) 박스의 모서리 — 같은 이유로 박스 안 · 원 밖(4,101m)
        fixture.savePublicCourse(COURSE_AT_3KM_BOX_CORNER,
                SEOUL_LAT + latOffset(2900), SEOUL_LNG + lngOffset(2900, SEOUL_LAT));

        // when : 콜드(전 셀 미스 → DB 채움) → 웜(전 셀 히트 → 캐시) → 직행(광역 가드로 강등)
        List<CourseMapDto> cold = reader.findCoursesForMap(SEOUL_LAT, SEOUL_LNG, MAP_RADIUS_M);
        List<CourseMapDto> warm = reader.findCoursesForMap(SEOUL_LAT, SEOUL_LNG, MAP_RADIUS_M);
        List<CourseMapDto> direct = reader.findCoursesForMap(SEOUL_LAT, SEOUL_LNG, MAX_CACHEABLE_RADIUS_M + 1);

        // then : 캐시를 거친 후보와 DB에서 갓 나온 후보가 같다
        assertThat(names(warm)).containsExactlyInAnyOrderElementsOf(names(cold));
        assertThat(names(cold)).containsExactlyInAnyOrder(COURSE_AT_CENTER, COURSE_NEAR_CENTER);

        // then : 두 경로 모두 자기 박스의 모서리 코스를 원 필터로 떨어뜨린다
        //        (1km 모서리 코스는 3,001m 요청에서는 원 안이므로 포함되는 것이 정답이다)
        assertThat(names(direct))
                .containsExactlyInAnyOrder(COURSE_AT_CENTER, COURSE_NEAR_CENTER, COURSE_AT_1KM_BOX_CORNER);
        assertThat(names(direct)).doesNotContain(COURSE_AT_3KM_BOX_CORNER);
    }

    /**
     * 채움 쿼리가 fill-limit에 걸리는 경우. fill-limit이 상수가 아니라 주입값이라(설계 D8) 코스 3개로 재현한다.
     *
     * <p>중첩 클래스는 별도 애플리케이션 컨텍스트를 쓰므로 바깥의 빈을 물려받지 않는다 — 필요한 빈을 다시 주입받는다.</p>
     */
    @DisplayName("채움 쿼리가 fill-limit에 걸린 경우")
    @TestPropertySource(properties = "course.cache.cell-bucket.fill-limit=2")
    @Nested
    class FillLimitReached {

        @Autowired CourseReadModelReader reader;
        @Autowired CourseReadModelRepository readModelRepository;
        @Autowired CourseRepository courseRepository;
        @Autowired MemberRepository memberRepository;
        @Autowired StringRedisTemplate stringRedisTemplate;

        private MapFixture fixture;

        @BeforeEach
        void setUp() {
            fixture = new MapFixture(memberRepository, courseRepository, readModelRepository, stringRedisTemplate);
            fixture.clearCellKeys();
        }

        @DisplayName("응답은 그대로 내되 적재는 전체 스킵한다 - 편향된 값이 캐시에 각인되지 않는다")
        @Test
        void skipsCachingEntirely_WhenFillQueryReachesLimit() {
            // given : 좁은 영역에 코스 3개 — 채움 쿼리 LIMIT(2)에 걸린다
            fixture.savePublicCourse("코스1", SEOUL_LAT, SEOUL_LNG);
            fixture.savePublicCourse("코스2", SEOUL_LAT + latOffset(100), SEOUL_LNG);
            fixture.savePublicCourse("코스3", SEOUL_LAT + latOffset(200), SEOUL_LNG);

            // when
            List<CourseMapDto> courses = reader.findCoursesForMap(SEOUL_LAT, SEOUL_LNG, MAP_RADIUS_M);

            // then : 응답은 정상적으로 나간다. 다만 fill-limit은 채움 쿼리의 LIMIT이기도 해서 잘린 2건 기반이다 (설계 §3-7)
            assertThat(courses).hasSize(2);
            assertThat(names(courses)).isSubsetOf("코스1", "코스2", "코스3");

            // then : 공간적으로 편향된 이 결과가 TTL 600초 동안 각인되는 것만 막으면 된다
            assertThat(fixture.cellKeys()).isEmpty();
        }
    }

    // ========== 픽스처 ==========

    private static List<String> names(List<CourseMapDto> courses) {
        return courses.stream().map(CourseMapDto::name).toList();
    }

    /**
     * 남쪽/북쪽 오프셋을 미터로 지정한다.
     *
     * <p>{@link BoundingBox}와 <b>같은 지구 근사</b>를 쓴다 — 픽스처가 "박스 안 · 원 밖"인지가 이 근사로 판정되기 때문이다.</p>
     */
    private static double latOffset(double meters) {
        return meters / (BoundingBox.KILOMETERS_PER_LAT_DEGREE * 1000);
    }

    /** 동쪽 오프셋을 미터로 지정한다. 경도는 위도에 따라 줄어들므로 기준 위도의 cos로 보정한다. */
    private static double lngOffset(double meters, double atLat) {
        return meters / (BoundingBox.KILOMETERS_PER_LAT_DEGREE * 1000 * Math.cos(Math.toRadians(atLat)));
    }

    /**
     * 지도 조회의 최소 전제(공개 코스 + 리드모델)를 만들고, 생긴 셀 키를 들여다본다.
     *
     * <p>중첩 테스트 클래스가 각자의 컨텍스트에서 같은 픽스처를 써야 해서 빈을 주입받는 형태로 둔다.</p>
     */
    private record MapFixture(MemberRepository memberRepository,
                              CourseRepository courseRepository,
                              CourseReadModelRepository readModelRepository,
                              StringRedisTemplate redisTemplate) {

        void savePublicCourse(String name, double lat, double lng) {
            Member owner = memberRepository.save(
                    Member.of("주인-" + name, "https://example.com/owner.jpg"));
            Course course = courseRepository.save(Course.of(
                    owner, name,
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

        Set<String> cellKeys() {
            Set<String> keys = redisTemplate.keys(CELL_KEY_PATTERN);
            return keys == null ? Set.of() : keys;
        }

        void clearCellKeys() {
            Set<String> keys = cellKeys();
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }

        String cellKey(GeoCell cell) {
            return CELL_KEY_PREFIX + cell.id();
        }

        String rawValue(GeoCell cell) {
            return redisTemplate.opsForValue().get(cellKey(cell));
        }
    }
}
