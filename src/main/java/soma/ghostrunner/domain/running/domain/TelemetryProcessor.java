package soma.ghostrunner.domain.running.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import soma.ghostrunner.domain.course.domain.StartPoint;
import soma.ghostrunner.domain.running.application.dto.ProcessedTelemetriesDto;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.global.common.error.ErrorCode;
import soma.ghostrunner.global.common.error.exception.ParsingException;

import java.util.ArrayList;
import java.util.List;

public class TelemetryProcessor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ProcessedTelemetriesDto processTelemetry(List<TelemetryDto> telemetries, Long startedAt) {

        List<TelemetryDto> relativeTelemetries = new ArrayList<>();
        List<Coordinate> coordinates = new ArrayList<>();
        Double highestPace = Double.MIN_VALUE;
        Double lowestPace = Double.MAX_VALUE;

        for (TelemetryDto telemetry : telemetries) {

            // 타임스탬프 상대시간으로 전환
            relativeTelemetries.add(telemetry.convertToRelativeTs(startedAt));

            // 좌표 수집
            coordinates.add(Coordinate.builder()
                    .lat(telemetry.lat())
                    .lng(telemetry.lng())
                    .build());

            // 속도 계산
            highestPace = Math.max(highestPace, telemetry.pace());
            lowestPace = Math.min(lowestPace, telemetry.pace());
        }

        return ProcessedTelemetriesDto.builder()
                .relativeTelemetries(relativeTelemetries)
                .startPoint(StartPoint.builder()
                        .latitude(telemetries.get(0).lat())
                        .longitude(telemetries.get(0).lng())
                        .build())
                .courseCoordinates(convertToString(coordinates))
                .highestPace(highestPace)
                .lowestPace(lowestPace)
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
            throw new ParsingException(ErrorCode.SERVICE_UNAVAILABLE, "시계열에서 위경도 데이터 추출을 실패했습니다.");
        }
    }
}
