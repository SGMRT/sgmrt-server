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
        List<CoordinateDto> pathData,
        List<MemberRecord> runners,
        Integer distance,
        Integer elevationGain,
        Integer elevationLoss
) {
    @Getter @AllArgsConstructor
    public static class MemberRecord {
        private String uuid;
        private String profileUrl;
    }
}

