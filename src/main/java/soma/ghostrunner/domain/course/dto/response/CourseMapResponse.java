package soma.ghostrunner.domain.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

public record CourseMapResponse(
        Long id,
        String name,
        Double startLat,
        Double startLng,
        String routeUrl,
        String thumbnailUrl,
        Integer distance,
        Integer elevationAverage,
        Integer elevationGain,
        Integer elevationLoss,
        LocalDateTime createdAt,

        List<MemberRecord> runners,
        long runnersCount
) {
    @Getter @AllArgsConstructor
    public static class MemberRecord {
        private String uuid;
        private String profileUrl;
    }
}

