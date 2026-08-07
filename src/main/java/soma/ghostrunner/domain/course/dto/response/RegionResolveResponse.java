package soma.ghostrunner.domain.course.dto.response;

/**
 * 지역 등록(resolve) 응답. {@code regionId}는 이후 코스 지도 조회의 캐시키로 쓰인다.
 *
 * 설계 문서: docs/refactoring/course-read-model/cache/05-cache-key-design.md §5-1, §6-4
 */
public record RegionResolveResponse(
        Long regionId,
        String name
) {}
