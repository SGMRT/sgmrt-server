package soma.ghostrunner.domain.course.domain;

/**
 * 요청 중심 좌표와 코스 좌표 사이의 실좌표 반경 판정.
 *
 * <p><b>{@link BoundingBox}와 반드시 같은 지구 근사</b>(위도 1도 = 111km, 경도는 중심 위도의 {@code cos} 보정)를 쓴다 —
 * 근사 상수는 {@link BoundingBox#KILOMETERS_PER_LAT_DEGREE} 한 곳에서만 정의하고 여기서는 참조만 한다.
 * {@code BoundingBox.of}가 {@code latDelta = r/111}, {@code lngDelta = r/(111·cos(centerLat))}로 박스를 만들므로,
 * 같은 근사로 거리를 재면 {@code dy² + dx² ≤ r²} ⟹ {@code |dy| ≤ r ∧ |dx| ≤ r} ⟹ 점이 박스 안,
 * 즉 <b>원 ⊆ 박스</b>가 부등식으로 증명된다.</p>
 *
 * <p>이 포함관계가 직행 경로(박스 조회 후 원 필터)와 캐시 경로(셀 커버링 후 원 필터)의 결과가 같다는
 * 파리티의 산술적 근거다. Haversine을 쓰면 포함관계가 얇은 고리에서 깨져 두 경로가 갈리므로,
 * 정확도 0.4%를 포기하고 "두 경로가 같다"는 증명 가능한 성질을 택한다 (설계 D1).</p>
 *
 * <p>{@code cos}는 점의 위도가 아니라 <b>요청 중심 위도</b>로 계산한다 — 박스와 같은 기준이어야 포함관계가 성립한다.</p>
 */
public final class GeoDistance {

    /**
     * 근사의 단일 출처는 {@link BoundingBox#KILOMETERS_PER_LAT_DEGREE}다 — 여기에 값을 복제하면
     * 한쪽만 바뀌었을 때 원 ⊆ 박스가 조용히 깨진다.
     */
    private static final double METERS_PER_LAT_DEGREE = BoundingBox.KILOMETERS_PER_LAT_DEGREE * 1000d;

    private GeoDistance() {
    }

    public static boolean withinRadius(double centerLat, double centerLng,
                                       double lat, double lng, double radiusM) {
        double dyM = (lat - centerLat) * METERS_PER_LAT_DEGREE;
        double dxM = (lng - centerLng) * METERS_PER_LAT_DEGREE * Math.cos(Math.toRadians(centerLat));

        return dyM * dyM + dxM * dxM <= radiusM * radiusM;   // sqrt 생략
    }
}
