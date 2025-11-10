package soma.ghostrunner.domain.running.domain.path;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
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
        Double courseDistance = 0.0;
        BigDecimal totalElevation = BigDecimal.ZERO;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(interpolatedTelemetry.getInputStream()))) {

            String line;

            double y = 0.0, x = 0.0;
            boolean isFirst = true;

            while ((line = reader.readLine()) != null) {

                if (line.trim().isEmpty()) {
                    continue;
                }

                // 읽기
                Telemetry telemetry = objectMapper.readValue(line, Telemetry.class);

                // 마이너스 검증
                verifyMinusValue(telemetry);

                // 총 거리 계산
                if ( isFirst ) {
                    isFirst = false;
                } else {
                    double distInterval = calculateDistanceM(y, x, telemetry.getY(), telemetry.getX());
                    courseDistance += distInterval;
                }

                // 현재 위경도로 업데이트
                y = telemetry.getY();
                x = telemetry.getX();

                // 상대시간 변환
                telemetry.calculateRelativeTimeStamp(startedAt);
                relativeTelemetries.add(telemetry);

                // 최고/최저 속도 계산
                highestPace = Math.max(highestPace, telemetry.getP());
                lowestPace = Math.min(lowestPace, telemetry.getP());

                totalElevation = totalElevation.add(BigDecimal.valueOf(telemetry.getE()));
            }
        } catch (IOException exception) {
            throw new TelemetryCalculationException(ErrorCode.SERVICE_UNAVAILABLE, "시계열 좌표를 가공하는 중 에러가 발생했습니다.");
        }

        if (relativeTelemetries.isEmpty()) {
            throw new IllegalArgumentException("Telemetry data is empty.");
        }

        // 평균 상대 고도 계산
        BigDecimal initialElevation = BigDecimal.valueOf(relativeTelemetries.get(0).getE());
        BigDecimal averageElevation = totalElevation.divide(BigDecimal.valueOf(relativeTelemetries.size()), 2, BigDecimal.ROUND_HALF_UP);
        averageElevation = averageElevation.subtract(initialElevation);

        return new TelemetryStatistics(
                relativeTelemetries,
                new Coordinates(relativeTelemetries.get(0).getY(), relativeTelemetries.get(0).getX()),
                highestPace,
                lowestPace,
                averageElevation.doubleValue(),
                Math.round((courseDistance / 1000) * 100.0) / 100.0
        );
    }

    private void verifyMinusValue(Telemetry telemetry) {
        if (telemetry.getP() < 0 || telemetry.getB() < 0
                || telemetry.getC() < 0 || telemetry.getD() < 0) {
            throw new InvalidRunningException(ErrorCode.INVALID_REQUEST_VALUE, "마이너스 값이 포함되어 있습니다.");
        }
    }

    private double calculateDistanceM(double lat1, double lon1, double lat2, double lon2) {

        double R = 6371000; // 지구 반지름 (m)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

}
