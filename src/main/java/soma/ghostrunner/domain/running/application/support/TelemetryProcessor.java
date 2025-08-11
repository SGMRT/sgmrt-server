package soma.ghostrunner.domain.running.application.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;
import soma.ghostrunner.domain.running.application.dto.ProcessedTelemetriesDto;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.domain.running.exception.TelemetryCalculationException;
import soma.ghostrunner.global.error.ErrorCode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class TelemetryProcessor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ProcessedTelemetriesDto process(MultipartFile interpolatedTelemetry) {

        List<TelemetryDto> relativeTelemetries = new ArrayList<>();
        List<CoordinateDto> coordinates = new ArrayList<>();

        Double highestPace = Double.MIN_VALUE;
        Double lowestPace = Double.MAX_VALUE;

        BigDecimal totalElevation = BigDecimal.ZERO;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(interpolatedTelemetry.getInputStream()))) {
            String line;
            long ts = 0;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                // 읽기
                TelemetryDto telemetryDto = objectMapper.readValue(line, TelemetryDto.class);

                // 마이너스 검증
                verifyMinusValue(telemetryDto);

                // 상대시간 변환
                telemetryDto.setRelativeTimeStamp(ts);
                relativeTelemetries.add(telemetryDto);

                // 좌표 수집
                coordinates.add(new CoordinateDto(telemetryDto.getLat(), telemetryDto.getLng()));

                // 최고/최저 속도 계산
                highestPace = Math.max(highestPace, telemetryDto.getPace());
                lowestPace = Math.min(lowestPace, telemetryDto.getPace());

                totalElevation = totalElevation.add(BigDecimal.valueOf(telemetryDto.getAlt()));
                ts += 1;
            }
        } catch (IOException exception) {
            throw new TelemetryCalculationException(ErrorCode.SERVICE_UNAVAILABLE, "시계열 좌표를 가공하는 중 에러가 발생했습니다.");
        }

        if (relativeTelemetries.isEmpty()) {
            throw new IllegalArgumentException("Telemetry data is empty.");
        }

        // 평균 상대 고도 계산
        BigDecimal averageElevation = totalElevation.divide(BigDecimal.valueOf(relativeTelemetries.size()), 2, BigDecimal.ROUND_HALF_UP);
        averageElevation = averageElevation.subtract(BigDecimal.valueOf(relativeTelemetries.get(0).getAlt()));

        return new ProcessedTelemetriesDto(
                relativeTelemetries,
                new CoordinateDto(relativeTelemetries.get(0).getLat(), relativeTelemetries.get(0).getLng()),
                coordinates,
                highestPace,
                lowestPace,
                averageElevation.doubleValue()
        );
    }

    private static void verifyMinusValue(TelemetryDto telemetryDto) {
        if (telemetryDto.getPace() < 0 || telemetryDto.getBpm() < 0
                || telemetryDto.getCadence() < 0 || telemetryDto.getDist() < 0) {
            throw new InvalidRunningException(ErrorCode.INVALID_REQUEST_VALUE, "마이너스 값이 포함되어 있습니다.");
        }
    }

}
