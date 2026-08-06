package soma.ghostrunner.domain.course.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * geohash p6 셀의 도메인 불변식 테스트. (설계: docs/design/course-cell-bucket-cache-design.md §3-1, §3-3)
 *
 * <p>검증하는 불변식은 다섯 가지다.
 * <ul>
 *     <li>인덱스 산술 인코딩이 표준 geohash(이진 탐색)와 동일하다</li>
 *     <li>encode/decode가 서로의 역함수다 (셀 경계 ↔ 좌표 왕복)</li>
 *     <li>반경 안의 모든 점은 커버링 셀 집합 안에 있다 — "원 안의 코스가 커버링 밖 셀에 저장"이 불가능</li>
 *     <li>O(1)로 구한 coveringCount가 실제 열거 개수와 같다 — 상한 가드가 열거와 어긋나지 않는다</li>
 *     <li>커버링 셀들의 합집합 박스가 각 셀의 경계를 모두 품는다 (미스 셀 채움 쿼리의 근거)</li>
 * </ul>
 */
class GeoCellTest {

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

    private static final double SEOUL_LAT = 37.5665;
    private static final double SEOUL_LNG = 126.9780;

    /** 설계상 커버링 셀 수 상한(MAX_COVERING_CELLS) — 이 값을 넘으면 조회가 직행 경로로 강등된다. */
    private static final int MAX_COVERING_CELLS = 128;

    /** 커버링 검증용 중심 좌표 — 위도대별로 셀의 경도 폭이 달라지므로 저위도부터 고위도까지 흩어 둔다. */
    private static final List<Center> CENTERS = List.of(
            new Center("서울", SEOUL_LAT, SEOUL_LNG),
            new Center("제주", 33.4996, 126.5312),
            new Center("적도", 0.0, 100.0),
            new Center("고위도", 65.0, 25.0)
    );

    private static final int[] RADII = {1000, 2000, 3000};

    private record Center(String name, double lat, double lng) {
    }

    @Test
    @DisplayName("인덱스 산술로 만든 셀 ID가 표준 geohash p6(이진 탐색)와 전 지구 랜덤 10,000점에서 완전히 일치한다")
    void of_MatchesStandardGeohashPrecision6() {
        // given
        Random random = new Random(42L);

        // when & then - 알려진 값
        assertThat(GeoCell.of(SEOUL_LAT, SEOUL_LNG).id()).isEqualTo("wydm9q");

        // when & then - 랜덤 10,000점 대조, 불일치 0건
        for (int i = 0; i < 10_000; i++) {
            double lat = -89.9 + random.nextDouble() * 179.8;
            double lng = -179.9 + random.nextDouble() * 359.8;

            assertThat(GeoCell.of(lat, lng).id())
                    .as("표준 geohash 불일치. lat=%s, lng=%s", lat, lng)
                    .isEqualTo(referenceGeohash(lat, lng));
        }
    }

    @Test
    @DisplayName("셀의 경계는 자신을 만든 좌표를 포함하고, 경계 내부의 임의 점은 다시 같은 셀로 인코딩된다")
    void bounds_IsInverseOfEncoding() {
        // given
        Random random = new Random(7L);

        for (int i = 0; i < 1_000; i++) {
            double lat = -89.0 + random.nextDouble() * 178.0;
            double lng = -179.0 + random.nextDouble() * 358.0;

            // when
            GeoCell cell = GeoCell.of(lat, lng);
            BoundingBox bounds = cell.bounds();

            // then - 원래 좌표가 셀 경계 안에 있다
            assertThat(lat).isBetween(bounds.minLat(), bounds.maxLat());
            assertThat(lng).isBetween(bounds.minLng(), bounds.maxLng());

            // then - 경계 내부의 임의 점은 같은 셀로 인코딩된다 (경계선 자체는 인접 셀과의 부동소수 경합이므로 제외)
            double innerLat = interpolate(bounds.minLat(), bounds.maxLat(), random);
            double innerLng = interpolate(bounds.minLng(), bounds.maxLng(), random);
            assertThat(GeoCell.of(innerLat, innerLng)).isEqualTo(cell);
        }
    }

    @Test
    @DisplayName("반경 안의 모든 점이 속한 셀은 커버링 결과에 빠짐없이 포함된다")
    void covering_ContainsEveryCellWithinRadius() {
        // given
        Random random = new Random(11L);

        for (Center center : CENTERS) {
            for (int radiusM : RADII) {
                // when
                Set<GeoCell> covering = Set.copyOf(GeoCell.covering(center.lat(), center.lng(), radiusM));

                // then - 반경 내부 랜덤 1,000점의 셀이 전부 커버링 안에 있다
                for (int i = 0; i < 1_000; i++) {
                    double angle = random.nextDouble() * 2 * Math.PI;
                    double distanceM = radiusM * Math.sqrt(random.nextDouble());
                    double lat = center.lat() + (distanceM * Math.sin(angle)) / 111_000d;
                    double lng = center.lng() + (distanceM * Math.cos(angle))
                            / (111_000d * Math.cos(Math.toRadians(center.lat())));

                    assertThat(covering)
                            .as("커버링 누락. center=%s, r=%s, point=(%s, %s)", center.name(), radiusM, lat, lng)
                            .contains(GeoCell.of(lat, lng));
                }
            }
        }
    }

    @Test
    @DisplayName("반경 경계값에서 커버링 셀 수가 정해진 규칙을 따르고 coveringCount가 실제 열거 수와 같다")
    void covering_BoundaryCases() {
        // given & when & then - 음수 반경이면 인덱스 범위가 역전되어 빈 리스트 (셀 한 변보다 큰 음수 반경)
        assertThat(GeoCell.covering(SEOUL_LAT, SEOUL_LNG, -1000)).isEmpty();
        assertThat(GeoCell.coveringCount(SEOUL_LAT, SEOUL_LNG, -1000)).isZero();

        // when & then - 반경 0이면 자기 자신이 속한 셀 1개
        assertThat(GeoCell.covering(SEOUL_LAT, SEOUL_LNG, 0))
                .containsExactly(GeoCell.of(SEOUL_LAT, SEOUL_LNG));

        // when & then - 서울 r=2000은 극단 좌표 가드 미만이어야 한다 (실사용 반경이 직행으로 강등되면 안 된다)
        assertThat(GeoCell.covering(SEOUL_LAT, SEOUL_LNG, 2000)).hasSizeLessThan(MAX_COVERING_CELLS);

        // when & then - O(1) 개수 계산이 실제 열거 결과와 일치한다 (가드 판정의 근거)
        for (Center center : CENTERS) {
            for (int radiusM : RADII) {
                assertThat(GeoCell.coveringCount(center.lat(), center.lng(), radiusM))
                        .as("coveringCount 불일치. center=%s, r=%s", center.name(), radiusM)
                        .isEqualTo(GeoCell.covering(center.lat(), center.lng(), radiusM).size());
            }
        }
    }

    @Test
    @DisplayName("셀 집합의 합집합 박스는 모든 셀의 경계를 포함하고, 빈 입력은 예외를 던진다")
    void enclosingBox_CoversEveryCellBounds() {
        // given
        List<GeoCell> cells = GeoCell.covering(SEOUL_LAT, SEOUL_LNG, 2000);

        // when
        BoundingBox enclosing = GeoCell.enclosingBox(cells);

        // then
        for (GeoCell cell : cells) {
            BoundingBox bounds = cell.bounds();
            assertThat(enclosing.minLat()).isLessThanOrEqualTo(bounds.minLat());
            assertThat(enclosing.maxLat()).isGreaterThanOrEqualTo(bounds.maxLat());
            assertThat(enclosing.minLng()).isLessThanOrEqualTo(bounds.minLng());
            assertThat(enclosing.maxLng()).isGreaterThanOrEqualTo(bounds.maxLng());
        }

        // then - 합집합 박스는 어느 셀보다도 넓다 (min/max 접기가 실제로 일어났다)
        assertThat(enclosing).isNotEqualTo(cells.get(0).bounds());

        // then - 빈 입력은 접을 대상이 없으므로 예외
        assertThatThrownBy(() -> GeoCell.enclosingBox(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoundingBox.union(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private double interpolate(double min, double max, Random random) {
        double fraction = 0.001 + random.nextDouble() * 0.998;
        return min + (max - min) * fraction;
    }

    /**
     * 표준 geohash 참조 구현 — 위도/경도 구간을 번갈아 이분하며 30비트를 만든다.
     * 프로덕션 코드(인덱스 산술)와 독립적인 알고리즘이어야 대조에 의미가 있으므로 의도적으로 고전 방식으로 작성한다.
     */
    private String referenceGeohash(double lat, double lng) {
        double[] latRange = {-90.0, 90.0};
        double[] lngRange = {-180.0, 180.0};

        StringBuilder geohash = new StringBuilder();
        boolean isLngTurn = true;
        int bitCount = 0;
        int charIndex = 0;

        while (geohash.length() < 6) {
            double[] range = isLngTurn ? lngRange : latRange;
            double value = isLngTurn ? lng : lat;
            double mid = (range[0] + range[1]) / 2;

            if (value >= mid) {
                charIndex = charIndex * 2 + 1;
                range[0] = mid;
            } else {
                charIndex = charIndex * 2;
                range[1] = mid;
            }
            isLngTurn = !isLngTurn;

            if (bitCount < 4) {
                bitCount++;
            } else {
                geohash.append(BASE32.charAt(charIndex));
                bitCount = 0;
                charIndex = 0;
            }
        }
        return geohash.toString();
    }
}
