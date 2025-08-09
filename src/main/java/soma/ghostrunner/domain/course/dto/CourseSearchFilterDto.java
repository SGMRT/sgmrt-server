package soma.ghostrunner.domain.course.dto;

import lombok.*;


/** 위치 기반 코스 조회를 위한 선택적 필터 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseSearchFilterDto {
    private Integer minDistanceM;
    private Integer maxDistanceM;
    private Integer minElevationM;
    private Integer maxElevationM;
    private String ownerUuid;

    public static CourseSearchFilterDto of() {
        return new CourseSearchFilterDto();
    }

    public static CourseSearchFilterDto of(Integer minDistanceM, Integer maxDistanceM,
                                           Integer minElevationM, Integer maxElevationM, String ownerUuid) {
        return new CourseSearchFilterDto(minDistanceM, maxDistanceM, minElevationM, maxElevationM, ownerUuid);
    }
}
