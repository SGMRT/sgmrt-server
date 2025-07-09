package soma.ghostrunner.domain.running.domain.support;

import soma.ghostrunner.domain.course.domain.StartPoint;
import soma.ghostrunner.domain.running.application.dto.CourseCoordinateDto;
import soma.ghostrunner.domain.running.application.dto.ProcessedTelemetriesDto;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;

import java.util.ArrayList;
import java.util.List;

public class TelemetryCalculator {

    public static ProcessedTelemetriesDto processTelemetry(List<TelemetryDto> telemetries, Long startedAt) {

        List<TelemetryDto> relativeTelemetries = new ArrayList<>();
        List<CourseCoordinateDto> coordinates = new ArrayList<>();
        Double highestPace = Double.MIN_VALUE;
        Double lowestPace = Double.MAX_VALUE;

        for (TelemetryDto telemetry : telemetries) {

            // 타임스탬프 상대시간으로 전환
            relativeTelemetries.add(telemetry.convertToRelativeTs(startedAt));

            // 좌표 수집
            coordinates.add(CourseCoordinateDto.builder()
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
                .courseCoordinates(CourseCoordinateConverter.convertToString(coordinates))
                .highestPace(highestPace)
                .lowestPace(lowestPace)
                .build();
    }

}
