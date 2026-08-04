package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.dto.query.CourseMapDto;

import java.util.List;
import java.util.Locale;

/**
 * 지도(주변 코스) 조회 유즈케이스 — 리드모델 읽기 진입점이자 캐시 지점.
 *
 * <p>캐시 설계 (docs/refactoring/course-read-model/04-detailed-design.md §2)</p>
 * <ul>
 *   <li><b>결과셋 캐시</b> — 좌표를 소수 2자리(≈1.1km 격자)로 반올림한 키로 쿼리 결과 전체를 캐싱한다.
 *       키: {@code round(lat,2):round(lng,2):radiusM}, TTL 60초.</li>
 *   <li><b>무효화 없음</b> — 쓰기 경로({@link CourseReadModelWriter})는 캐시를 모른다.
 *       데이터 변경 후 최대 TTL(60초)의 스테일을 허용하며, 리드모델(DB)은 항상 즉시 정정되므로
 *       오류는 일시적이고 자가 치유된다.</li>
 *   <li><b>기본 요청만 캐싱</b> — 정렬/필터가 기본값이 아닌 요청은 캐시를 우회한다(키 공간 오염 방지).</li>
 *   <li><b>bbox 는 반올림된 중심 기준</b> — 같은 키의 모든 사용자가 동일한 결과를 받도록 결정적으로
 *       계산한다. 실제 위치와 최대 ~780m 어긋난 bbox 가 되지만 기본 반경 2km 미리보기에서 허용.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CourseReadModelReader {

    public static final String COURSE_MAP_CACHE = "course-map";

    /** 랜덤 선별의 모집단 확보를 위해 응답 개수(10)보다 넉넉히 조회한다. */
    private static final int MAP_QUERY_LIMIT = 50;

    private final CourseReadModelRepository readModelRepository;

    /**
     * 반올림된 중심 기준 바운딩박스 안의 공개 코스 카드(TOP4 러너 포함)를 조회한다.
     *
     * @param cacheable 기본 요청(정렬·필터 기본값) 여부 — false 면 캐시를 우회한다
     */
    @Cacheable(cacheNames = COURSE_MAP_CACHE,
               condition = "#cacheable",
               key = "T(soma.ghostrunner.domain.course.application.CourseReadModelReader).cacheKey(#lat, #lng, #radiusM)")
    public List<CourseMapDto> findCoursesForMap(double lat, double lng, int radiusM, boolean cacheable) {
        double centerLat = roundToCell(lat);
        double centerLng = roundToCell(lng);
        CourseService.LatLngs bounds = CourseService.getBoundingBoxLatLngs(centerLat, centerLng, radiusM);

        return readModelRepository.findCoursesForMap(
                bounds.minLat(), bounds.maxLat(), bounds.minLng(), bounds.maxLng(), MAP_QUERY_LIMIT);
    }

    /** 캐시 키 = 반올림 좌표 + 반경. 같은 1.1km 격자의 요청이 하나의 키로 뭉친다. */
    public static String cacheKey(double lat, double lng, int radiusM) {
        return String.format(Locale.US, "%.2f:%.2f:%d", roundToCell(lat), roundToCell(lng), radiusM);
    }

    private static double roundToCell(double coordinate) {
        return Math.round(coordinate * 100) / 100.0;
    }
}
