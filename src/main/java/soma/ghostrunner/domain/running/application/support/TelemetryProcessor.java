package soma.ghostrunner.domain.running.application.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;
import soma.ghostrunner.domain.running.application.dto.ProcessedTelemetriesDto;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class TelemetryProcessor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ProcessedTelemetriesDto process(MultipartFile telemetryFile) throws IOException {

        List<TelemetryDto> relativeTelemetries = new ArrayList<>();
        List<CoordinateDto> coordinates = new ArrayList<>();
        Double highestPace = Double.MIN_VALUE;
        Double lowestPace = Double.MAX_VALUE;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(telemetryFile.getInputStream()))) {
            String line;
            long ts = 0;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                // 읽기
                TelemetryDto telemetryDto = objectMapper.readValue(line, TelemetryDto.class);

                // 상대시간 변환
                telemetryDto.setRelativeTimeStamp(ts);
                relativeTelemetries.add(telemetryDto);

                // 좌표 수집
                coordinates.add(new CoordinateDto(telemetryDto.getLat(), telemetryDto.getLng()));

                // 최고/최저 속도 계산
                highestPace = Math.max(highestPace, telemetryDto.getPace());
                lowestPace = Math.min(lowestPace, telemetryDto.getPace());

                ts += 1;
            }
        }

        if (relativeTelemetries.isEmpty()) {
            throw new IllegalArgumentException("Telemetry data is empty.");
        }

        return new ProcessedTelemetriesDto(
                relativeTelemetries,
                new CoordinateDto(relativeTelemetries.get(0).getLat(), relativeTelemetries.get(0).getLng()),
                coordinates,
                highestPace,
                lowestPace
        );
    }

}
