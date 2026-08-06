package soma.ghostrunner.domain.course.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GeoDistance 순수 단위 테스트 (Spring 컨텍스트/DB 없음)
 *
 * 설계 문서: docs/design/course-cell-bucket-cache-design.md §3-2
 *
 * 검증 대상은 GeoDistance와 BoundingBox가 같은 지구 근사를 공유해서 성립하는 단 하나의 성질,
 * 즉 "원 ⊆ 박스"(설계 D1)다. 이 포함관계가 직행 경로(박스 조회 후 원 필터)와
 * 캐시 경로(셀 커버링 후 원 필터)의 결과가 같다는 파리티의 산술적 근거다.
 *
 * 거리값 자체나 Haversine 대조는 검증하지 않는다 — D1에 의해 의도적으로 다른 근사를 쓴다.
 */
@DisplayName("GeoDistance 단위 테스트")
class GeoDistanceTest {

    private static final int SAMPLE_COUNT = 10_000;
    private static final long SEED = 20260806L;

    @Test
    @DisplayName("원 안에 있다고 판정된 점은 반드시 같은 중심·반경으로 만든 BoundingBox 안에 있다")
    void withinRadiusImpliesWithinBoundingBox() {
        // given
        Random random = new Random(SEED);
        int insideCircleCount = 0;

        for (int i = 0; i < SAMPLE_COUNT; i++) {
            double centerLat = randomBetween(random, -60.0, 60.0);
            double centerLng = randomBetween(random, -180.0, 180.0);
            double radiusM = randomBetween(random, 100.0, 3000.0);

            BoundingBox box = BoundingBox.of(centerLat, centerLng, radiusM);
            double latSpan = box.maxLat() - centerLat;
            double lngSpan = box.maxLng() - centerLng;

            // 박스 경계 안팎이 섞이도록 박스보다 1.5배 넓은 범위에서 점을 뽑는다
            double lat = centerLat + randomBetween(random, -1.5 * latSpan, 1.5 * latSpan);
            double lng = centerLng + randomBetween(random, -1.5 * lngSpan, 1.5 * lngSpan);

            // when
            boolean withinRadius = GeoDistance.withinRadius(centerLat, centerLng, lat, lng, radiusM);

            // then
            if (withinRadius) {
                insideCircleCount++;
                assertThat(lat)
                        .as("원 안의 점(lat=%s)이 박스 위도 범위[%s, %s]를 벗어남 (center=%s, r=%s)",
                                lat, box.minLat(), box.maxLat(), centerLat, radiusM)
                        .isBetween(box.minLat(), box.maxLat());
                assertThat(lng)
                        .as("원 안의 점(lng=%s)이 박스 경도 범위[%s, %s]를 벗어남 (center=%s, r=%s)",
                                lng, box.minLng(), box.maxLng(), centerLng, radiusM)
                        .isBetween(box.minLng(), box.maxLng());
            }
        }

        // 표본이 전부 원 밖이면 위 단언이 한 번도 실행되지 않아 검증이 공허해진다
        assertThat(insideCircleCount).isPositive();
    }

    private double randomBetween(Random random, double min, double max) {
        return min + random.nextDouble() * (max - min);
    }
}
