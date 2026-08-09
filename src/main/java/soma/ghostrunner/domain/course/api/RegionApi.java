package soma.ghostrunner.domain.course.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import soma.ghostrunner.domain.course.application.RegionResolver;
import soma.ghostrunner.domain.course.domain.Region;
import soma.ghostrunner.domain.course.dto.CourseMapper;
import soma.ghostrunner.domain.course.dto.request.RegionResolveRequest;
import soma.ghostrunner.domain.course.dto.response.RegionResolveResponse;

/**
 * 지역 등록(resolve) API — 클라이언트가 리버스 지오코딩으로 얻은 지역 이름을 코스 지도 캐시키(regionId)로 교환한다.
 *
 * <p>멱등 계약: 같은 {@code name}은 몇 번을 호출하든 항상 같은 {@code regionId}를 반환한다
 * (없으면 등록, 있으면 기존 행 반환 — 둘 다 200).
 *
 * 설계 문서: docs/refactoring/course-read-model/cache/05-cache-key-design.md §5-1, §6-4
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class RegionApi {

    private final RegionResolver regionResolver;
    private final CourseMapper courseMapper;

    @PostMapping("/regions")
    public RegionResolveResponse resolveRegion(@Valid @RequestBody RegionResolveRequest request) {
        Region region = regionResolver.resolve(request.name(), request.lat(), request.lng());
        return courseMapper.toRegionResolveResponse(region);
    }
}
