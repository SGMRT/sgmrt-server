package soma.ghostrunner.domain.course.dto.response;

import soma.ghostrunner.domain.course.enums.CourseSource;

import java.time.LocalDateTime;
import java.util.List;

public record CourseMapResponse(
        Long id,
        String name,
        CourseSource source,
        Double startLat,
        Double startLng,
        String routeUrl,
        String checkpointsUrl,
        String thumbnailUrl,
        Integer distance,
        Integer elevationAverage,
        Integer elevationGain,
        Integer elevationLoss,
        LocalDateTime createdAt,

        CourseGhostResponse myGhostInfo,
        List<MemberRecord> runners,
        long runnersCount
) {
    public record MemberRecord(
            String uuid,
            String profileUrl
    ) {}
}

