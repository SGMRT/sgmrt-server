package soma.ghostrunner.domain.course.domain;

/**
 * (lat, lng)을 radiusM로 둘러싼 직사각형 경계 좌표.
 *
 * <p>조회 박스(CourseReadModelReader·CourseService)와 이빅트 역산 박스(CourseMapCacheEvictListener)는
 * 반드시 같은 공식으로 계산되어야 역산이 조회의 상위집합이라는 불변식이 성립한다 — 복제 금지, 이 팩토리만 사용한다.</p>
 */
public record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {

    /**
     * 1도 위도 당 111km 가정 (지구 둘레 40,075km / 360도 = 약 111.3km).
     * 근사치이며 적도에서 멀어질수록 경도 거리 오차가 커짐 -> TODO: 추후 Haversine 공식이나 DB 공간 데이터 타입 활용
     */
    public static BoundingBox of(double lat, double lng, double radiusM) {
        double radiusKm = radiusM / 1000d;
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));

        return new BoundingBox(lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta);
    }
}
