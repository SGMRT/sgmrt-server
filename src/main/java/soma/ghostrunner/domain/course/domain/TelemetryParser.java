package soma.ghostrunner.domain.course.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import soma.ghostrunner.domain.running.application.dto.TelemetryCommand;

import java.util.List;
import java.util.stream.Collectors;

public class TelemetryParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static List<TelemetryCommand> convertAbsoluteToRelativeTimestamp(List<TelemetryCommand> telemetries, Long startedAt) {
        return telemetries.stream()
                .map(t -> t.withTimeStamp(t.timeStamp() - startedAt))
                .collect(Collectors.toList());
    }

    public static StartPoint extractStartPoint(List<TelemetryCommand> telemetries) {
        TelemetryCommand startPointTelemetry = telemetries.get(0);
        return StartPoint.builder()
                .latitude(startPointTelemetry.lat())
                .longitude(startPointTelemetry.lng())
                .build();
    }

    public static String extractCourseCoordinates(List<TelemetryCommand> telemetries) {
        List<Coordinate> coordinates = telemetries.stream()
                .map(t -> Coordinate.builder()
                        .lat(t.lat())
                        .lng(t.lng())
                        .build())
                .toList();

        return convertToString(coordinates);
    }

    @Getter
    @Builder @AllArgsConstructor
    private static class Coordinate {
        private double lat;
        private double lng;
    }

    private static String convertToString(List<Coordinate> coordinates) {
        try {
            return objectMapper.writeValueAsString(coordinates);
        } catch (Exception e) {
            throw new RuntimeException("시계열에서 위경도 데이터 추출을 실패했습니다.");
        }
    }
}
