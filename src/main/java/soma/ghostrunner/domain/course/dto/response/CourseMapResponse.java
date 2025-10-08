package soma.ghostrunner.domain.course.dto.response;

import soma.ghostrunner.domain.course.enums.CourseSource;

import java.time.LocalDateTime;
import java.util.List;

public record CourseMapResponse(
        Long id,
        String name,
        String ownerUuid,
        CourseSource source,
        Double startLat,
        Double startLng,
        String routeUrl,
        List<RunnerInfo> top4Runners,

        long runnersCount,
        boolean hasMyRecord,
        Integer distance,
        Integer elevation,
        String thumbnailUrl,

        LocalDateTime createdAt
) {
    public record RunnerInfo(
            String uuid,
            String profileUrl
    ) {}
}

