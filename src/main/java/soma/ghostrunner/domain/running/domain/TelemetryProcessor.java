package soma.ghostrunner.domain.running.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import soma.ghostrunner.domain.course.domain.StartPoint;
import soma.ghostrunner.domain.running.application.dto.ProcessedTelemetryResult;
import soma.ghostrunner.domain.running.application.dto.TelemetryCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TelemetryProcessor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ProcessedTelemetryResult processTelemetry(List<TelemetryCommand> telemetries, Long startedAt) {

        List<TelemetryCommand> relativeTelemetries = new ArrayList<>();
        List<Coordinate> coordinates = new ArrayList<>();

        for (TelemetryCommand telemetry : telemetries) {

            // 타임스탬프 상대시간으로 전환
            relativeTelemetries.add(telemetry.convertToRelativeTs(startedAt));

            // 좌표 수집
            coordinates.add(Coordinate.builder()
                    .lat(telemetry.lat())
                    .lng(telemetry.lng())
                    .build());
        }

        return ProcessedTelemetryResult.builder()
                .relativeTelemetries(relativeTelemetries)
                .startPoint(StartPoint.builder()
                        .latitude(telemetries.get(0).lat())
                        .longitude(telemetries.get(0).lng())
                        .build())
                .courseCoordinates(convertToString(coordinates))
                .build();
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
