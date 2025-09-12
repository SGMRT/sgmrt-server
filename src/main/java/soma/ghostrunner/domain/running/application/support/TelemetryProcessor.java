package soma.ghostrunner.domain.running.application.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.domain.running.domain.path.Coordinates;
import soma.ghostrunner.domain.running.domain.path.TelemetryStatistics;
import soma.ghostrunner.domain.running.domain.path.Telemetry;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.domain.running.exception.TelemetryCalculationException;
import soma.ghostrunner.global.error.ErrorCode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class TelemetryProcessor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public TelemetryStatistics process(MultipartFile interpolatedTelemetry, Long startedAt) {

        List<Telemetry> relativeTelemetries = new ArrayList<>();

        Double highestPace = Double.MIN_VALUE;
        Double lowestPace = Double.MAX_VALUE;

        BigDecimal totalElevation = BigDecimal.ZERO;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(interpolatedTelemetry.getInputStream()))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                // 읽기
                Telemetry telemetry = objectMapper.readValue(line, Telemetry.class);

                // 마이너스 검증
                verifyMinusValue(telemetry);

                // 상대시간 변환
                telemetry.calculateRelativeTimeStamp(startedAt);
                relativeTelemetries.add(telemetry);

                // 최고/최저 속도 계산
                highestPace = Math.max(highestPace, telemetry.getPace());
                lowestPace = Math.min(lowestPace, telemetry.getPace());

                totalElevation = totalElevation.add(BigDecimal.valueOf(telemetry.getAlt()));
            }
        } catch (IOException exception) {
            throw new TelemetryCalculationException(ErrorCode.SERVICE_UNAVAILABLE, "시계열 좌표를 가공하는 중 에러가 발생했습니다.");
        }

        if (relativeTelemetries.isEmpty()) {
            throw new IllegalArgumentException("Telemetry data is empty.");
        }

        // 평균 상대 고도 계산
        BigDecimal initialElevation = BigDecimal.valueOf(relativeTelemetries.get(0).getAlt());
        BigDecimal averageElevation = totalElevation.divide(BigDecimal.valueOf(relativeTelemetries.size()), 2, BigDecimal.ROUND_HALF_UP);
        averageElevation = averageElevation.subtract(initialElevation);

        return new TelemetryStatistics(
                relativeTelemetries,
                new Coordinates(relativeTelemetries.get(0).getLat(), relativeTelemetries.get(0).getLng()),
                highestPace,
                lowestPace,
                averageElevation.doubleValue()
        );
    }

    private void verifyMinusValue(Telemetry telemetry) {
        if (telemetry.getPace() < 0 || telemetry.getBpm() < 0
                || telemetry.getCadence() < 0 || telemetry.getDist() < 0) {
            throw new InvalidRunningException(ErrorCode.INVALID_REQUEST_VALUE, "마이너스 값이 포함되어 있습니다.");
        }
    }

}
