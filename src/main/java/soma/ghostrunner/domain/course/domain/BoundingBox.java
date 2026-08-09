package soma.ghostrunner.domain.course.domain;

import java.util.Collection;

/**
 * (lat, lng)을 radiusM로 둘러싼 직사각형 경계 좌표.
 *
 * <p>조회 박스(CourseReadModelReader·CourseReader)와 셀 커버링({@link GeoCell})은 반드시 같은 공식으로
 * 계산되어야 "원 안의 코스가 커버링 밖 셀에 저장되는" 경우가 없다 — 복제 금지, 이 팩토리만 사용한다.</p>
 *
 * <p>이 팩토리의 지구 근사는 거리 필터({@link GeoDistance})와 <b>공유하는 단일 출처</b>다.
 * 같은 근사를 쓰기 때문에 "원 ⊆ 박스"가 부등식으로 성립하고(설계 D1), 그것이 직행 경로(박스 조회 후 원 필터)와
 * 캐시 경로(셀 커버링 후 원 필터)의 결과가 같다는 파리티의 근거다.
 * 근사를 바꾸려면 {@link #KILOMETERS_PER_LAT_DEGREE} 한 곳만 바꾼다 — 양쪽에 복제하면 파리티가 조용히 깨진다.</p>
 */
public record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {

    /**
     * 1도 위도 당 111km 가정 (지구 둘레 40,075km / 360도 = 약 111.3km).
     * 근사치이며 적도에서 멀어질수록 경도 거리 오차가 커짐 -> TODO: 추후 Haversine 공식이나 DB 공간 데이터 타입 활용
     *
     * <p>{@link GeoDistance}가 이 값을 그대로 참조한다. 박스와 거리 필터의 근사가 어긋나면 원 ⊆ 박스가 깨진다.</p>
     */
    public static final double KILOMETERS_PER_LAT_DEGREE = 111.0;

    public static BoundingBox of(double lat, double lng, double radiusM) {
        double radiusKm = radiusM / 1000d;
        double latDelta = radiusKm / KILOMETERS_PER_LAT_DEGREE;
        double lngDelta = radiusKm / (KILOMETERS_PER_LAT_DEGREE * Math.cos(Math.toRadians(lat)));

        return new BoundingBox(lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta);
    }

    /**
     * 박스들을 모두 품는 최소 박스. min/max 접기.
     *
     * @throws IllegalArgumentException 입력이 비어 있으면 접을 대상이 없다.
     */
    public static BoundingBox union(Collection<BoundingBox> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            throw new IllegalArgumentException("박스가 비어 있어 합집합을 만들 수 없습니다.");
        }

        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double minLng = Double.POSITIVE_INFINITY;
        double maxLng = Double.NEGATIVE_INFINITY;

        for (BoundingBox box : boxes) {
            minLat = Math.min(minLat, box.minLat());
            maxLat = Math.max(maxLat, box.maxLat());
            minLng = Math.min(minLng, box.minLng());
            maxLng = Math.max(maxLng, box.maxLng());
        }
        return new BoundingBox(minLat, maxLat, minLng, maxLng);
    }
}
