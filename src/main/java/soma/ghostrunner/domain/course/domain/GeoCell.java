package soma.ghostrunner.domain.course.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * geohash precision 6 셀. 셀 버킷 캐시의 키 단위다. (설계: docs/design/course-cell-bucket-cache-design.md §3-1)
 *
 * <p>p6 = 30비트 = 경도 15비트 + 위도 15비트다. 셀 크기는 위도 방향 0.005493°(≈610m),
 * 경도 방향 0.010986°(적도 1,219m / 서울 967m)다.</p>
 *
 * <p><b>넣은 곳 = 지울 곳 = 찾는 곳.</b> 코스는 시작점 좌표가 정하는 단 하나의 셀에 적재되고({@link #of}),
 * 이빅트도 같은 좌표로 계산한 같은 셀 하나만 지운다. 조회는 요청 반경을 덮는 셀들을 방문할 뿐이다({@link #covering}).
 * 키의 주인이 요청자가 아니라 코스이기 때문에 저장 위치와 삭제 위치가 항상 1:1로 맞고, 팬아웃 삭제가 필요 없다.</p>
 *
 * <p><b>표준 이진 탐색 대신 격자 인덱스를 직접 계산하는 이유</b> — 커버링을 "인덱스 범위의 정수 순회"로 열거할 수 있게 되어,
 * 경계 셀을 건너뛰어 코스가 조용히 사라지는 샘플링 누락 실패 모드가 산수 수준에서 제거된다.
 * {@link #covering}이 {@link #of}와 같은 인코딩만 쓰므로 "반경 안의 코스가 커버링 밖 셀에 저장"되는 경우가 구조적으로 불가능하다.
 * 두 방식의 결과가 같다는 것은 테스트에서 표준 geohash 참조 구현과 대조해 확인한다.</p>
 *
 * <p><b>[R1] {@link #coveringCount}가 열거와 분리된 이유</b> — 커버링 상한 가드를 리스트 크기로 판정하면,
 * 가드가 필요한 바로 그 극단 좌표(고위도에서 {@code cos(lat)}이 0에 수렴해 경도 범위가 전 지구로 clamp되는 경우)에서
 * 수십만 개의 셀을 모두 할당한 뒤 버리게 된다. 개수는 인덱스 범위의 곱이므로 할당 없이 O(1)로 나온다.
 * 호출자는 개수로 먼저 판정하고 통과한 요청만 열거한다.</p>
 *
 * <p>antimeridian(±180)은 clamp만 하고 wrap하지 않는다.</p>
 */
public record GeoCell(String id) {

    private static final int BITS_PER_AXIS = 15;
    private static final int TOTAL_BITS = BITS_PER_AXIS * 2;    // p6 = 30비트
    private static final int AXIS_CELLS = 1 << BITS_PER_AXIS;   // 32,768

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";
    private static final int BITS_PER_CHAR = 5;                 // 32글자 = 5비트
    private static final int CHAR_MASK = (1 << BITS_PER_CHAR) - 1;
    private static final int ID_LENGTH = TOTAL_BITS / BITS_PER_CHAR;

    /** 격자 인덱스 0번 셀의 좌측 하단 좌표. 위도 정의역은 [-90, 90], 경도는 [-180, 180]이다. */
    private static final double LAT_ORIGIN = -90.0d;
    private static final double LNG_ORIGIN = -180.0d;

    /** 셀 한 변의 각도. 180 / 2^15, 360 / 2^15 — 둘 다 이진 부동소수로 정확히 표현된다(인코딩·경계 역산의 정합 근거). */
    private static final double LAT_SPAN = 180.0d / AXIS_CELLS;
    private static final double LNG_SPAN = 360.0d / AXIS_CELLS;

    public static GeoCell of(double lat, double lng) {
        return new GeoCell(encode(new CellIndex(latIndex(lat), lngIndex(lng))));
    }

    /**
     * 커버링 셀 개수만 O(1)로 계산한다. 극단 좌표에서 열거(할당) 전에 상한 가드를 판정하기 위한 것이다.
     *
     * @see #covering(double, double, int)
     */
    public static long coveringCount(double lat, double lng, int radiusM) {
        return indexRange(lat, lng, radiusM).cellCount();
    }

    /** 반경을 감싸는 조회 박스에 걸치는 셀 전부를 산술 열거한다. 반경이 음수면 빈 리스트다. */
    public static List<GeoCell> covering(double lat, double lng, int radiusM) {
        CellIndexRange range = indexRange(lat, lng, radiusM);

        List<GeoCell> cells = new ArrayList<>();
        for (int latIdx = range.latFrom(); latIdx <= range.latTo(); latIdx++) {
            for (int lngIdx = range.lngFrom(); lngIdx <= range.lngTo(); lngIdx++) {
                cells.add(new GeoCell(encode(new CellIndex(latIdx, lngIdx))));
            }
        }
        return cells;
    }

    /** 이 셀의 경계. encode의 역함수(decode)로 인덱스를 되찾아 계산한다. */
    public BoundingBox bounds() {
        CellIndex index = decode(id);
        double minLat = LAT_ORIGIN + index.latIdx() * LAT_SPAN;
        double minLng = LNG_ORIGIN + index.lngIdx() * LNG_SPAN;

        return new BoundingBox(minLat, minLat + LAT_SPAN, minLng, minLng + LNG_SPAN);
    }

    /** 셀들의 경계를 모두 품는 최소 박스. 미스 셀 채움 쿼리 1회의 범위가 된다. */
    public static BoundingBox enclosingBox(Collection<GeoCell> cells) {
        if (cells == null || cells.isEmpty()) {
            throw new IllegalArgumentException("셀이 비어 있어 합집합 박스를 만들 수 없습니다.");
        }
        return BoundingBox.union(cells.stream().map(GeoCell::bounds).toList());
    }

    /**
     * 커버링 대상 셀의 인덱스 범위.
     *
     * <p>covering과 coveringCount가 반드시 이 하나를 공유해야 개수 가드가 실제 열거와 어긋나지 않는다.</p>
     */
    private static CellIndexRange indexRange(double lat, double lng, int radiusM) {
        if (radiusM < 0) {
            return CellIndexRange.EMPTY;
        }

        BoundingBox box = BoundingBox.of(lat, lng, radiusM);
        return new CellIndexRange(
                latIndex(box.minLat()), latIndex(box.maxLat()),
                lngIndex(box.minLng()), lngIndex(box.maxLng()));
    }

    private static int latIndex(double lat) {
        return clamp((int) Math.floor((lat - LAT_ORIGIN) / LAT_SPAN));
    }

    private static int lngIndex(double lng) {
        return clamp((int) Math.floor((lng - LNG_ORIGIN) / LNG_SPAN));
    }

    private static int clamp(int index) {
        return Math.min(Math.max(index, 0), AXIS_CELLS - 1);
    }

    /** 격자 인덱스 → 30비트 인터리브 → base32 6글자. 각 단계의 역함수가 {@link #decode(String)}에 나란히 있다. */
    private static String encode(CellIndex index) {
        return toBase32(interleave(index));
    }

    /** {@link #encode(CellIndex)}의 정확한 역함수. */
    private static CellIndex decode(String id) {
        return deinterleave(fromBase32(id));
    }

    /** MSB부터 경도·위도 비트를 한 개씩 교대로 끼워 30비트를 만든다. 최상위 비트가 경도다. */
    private static int interleave(CellIndex index) {
        int bits = 0;
        for (int i = BITS_PER_AXIS - 1; i >= 0; i--) {
            bits = (bits << 1) | ((index.lngIdx() >> i) & 1);
            bits = (bits << 1) | ((index.latIdx() >> i) & 1);
        }
        return bits;
    }

    /** {@link #interleave(CellIndex)}의 역. 홀수 자리 비트는 경도로, 짝수 자리 비트는 위도로 되돌린다. */
    private static CellIndex deinterleave(int bits) {
        int latIdx = 0;
        int lngIdx = 0;
        for (int i = TOTAL_BITS - 1; i >= 0; i--) {
            int bit = (bits >> i) & 1;
            if (i % 2 == 1) {
                lngIdx = (lngIdx << 1) | bit;
            } else {
                latIdx = (latIdx << 1) | bit;
            }
        }
        return new CellIndex(latIdx, lngIdx);
    }

    /** 30비트를 상위 5비트씩 끊어 base32 6글자로 적는다. */
    private static String toBase32(int bits) {
        StringBuilder id = new StringBuilder(ID_LENGTH);
        for (int charIdx = ID_LENGTH - 1; charIdx >= 0; charIdx--) {
            id.append(BASE32.charAt((bits >> (charIdx * BITS_PER_CHAR)) & CHAR_MASK));
        }
        return id.toString();
    }

    /** {@link #toBase32(int)}의 역. 길이·문자가 base32 알파벳에 맞지 않으면 예외다. */
    private static int fromBase32(String id) {
        if (id == null || id.length() != ID_LENGTH) {
            throw new IllegalArgumentException("셀 ID 길이가 올바르지 않습니다. id=" + id);
        }

        int bits = 0;
        for (int charIdx = 0; charIdx < ID_LENGTH; charIdx++) {
            int charValue = BASE32.indexOf(id.charAt(charIdx));
            if (charValue < 0) {
                throw new IllegalArgumentException("셀 ID에 허용되지 않는 문자가 있습니다. id=" + id);
            }
            bits = (bits << BITS_PER_CHAR) | charValue;
        }
        return bits;
    }

    /** 한 셀의 격자 좌표. 두 인덱스 모두 0 이상 2^15 미만이다. */
    private record CellIndex(int latIdx, int lngIdx) {
    }

    /** 커버링 대상 셀의 격자 인덱스 사각 범위(양끝 포함). from > to면 열거할 셀이 없다. */
    private record CellIndexRange(int latFrom, int latTo, int lngFrom, int lngTo) {

        private static final CellIndexRange EMPTY = new CellIndexRange(0, -1, 0, -1);

        /** 열거하지 않고 곱으로만 구하는 셀 개수. 전 지구 clamp(2^15 × 2^15)에서도 넘치지 않도록 long이다. */
        private long cellCount() {
            if (latFrom > latTo || lngFrom > lngTo) {
                return 0L;
            }
            return (long) (latTo - latFrom + 1) * (lngTo - lngFrom + 1);
        }
    }
}
