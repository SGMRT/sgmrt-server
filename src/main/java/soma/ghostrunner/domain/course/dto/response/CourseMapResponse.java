package soma.ghostrunner.domain.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;

import java.util.List;

public record CourseMapResponse(
        Long id,
        String name,
        Double startLat,
        Double startLng,
        String routeUrl,
        String thumbnailUrl,
        Integer distance,
        Integer elevationGain,
        Integer elevationLoss,

        List<MemberRecord> runners,
        long runnersCount
) {
    @Getter @AllArgsConstructor
    public static class MemberRecord {
        private String uuid;
        private String profileUrl;
    }
}

