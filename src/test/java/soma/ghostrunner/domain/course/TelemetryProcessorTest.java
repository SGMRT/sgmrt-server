package soma.ghostrunner.domain.course;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.running.application.dto.ProcessedTelemetryResult;
import soma.ghostrunner.domain.running.domain.TelemetryProcessor;
import soma.ghostrunner.domain.course.domain.StartPoint;
import soma.ghostrunner.domain.running.application.dto.TelemetryCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class TelemetryProcessorTest {

    private final Long startedAt = 1750729987181L;
    private final List<TelemetryCommand> telemetryList = new ArrayList<>();

    @DisplayName("시계열에서 고도, 시작점, 위도+경도를 추출하고 타임스탬프를 상대 시간으로 변경한다.")
    @Test
    void processTelemetryTest() {
        // when
        setUp();
        ProcessedTelemetryResult processedTelemetryResult = TelemetryProcessor.processTelemetry(telemetryList, startedAt);

        // then
        // 상대시간
        for (int i = 0; i < telemetryList.size(); i++) {
            Assertions.assertThat(processedTelemetryResult.getRelativeTelemetries().get(i).timeStamp()).isEqualTo(i*5);
        }
        // 시작점 위도+경도
        Assertions.assertThat(processedTelemetryResult.getStartPoint().getLatitude()).isEqualTo(37.5665);
        Assertions.assertThat(processedTelemetryResult.getStartPoint().getLongitude()).isEqualTo(126.9780);
        // 코스 STRING
        System.out.println(processedTelemetryResult.getCourseCoordinates());
    }

    private void setUp() {
        Random random = new Random();

        for (int i = 0; i < 10; i++) {
            TelemetryCommand telemetry = new TelemetryCommand(
                    startedAt + (i*5),     // 5초 간격
                    37.5665 + i * 0.0001,         // 위도
                    126.9780 + i * 0.0001,        // 경도
                    i * 10.0,                    // 거리 (예: 10m 단위)
                    5.0 + (i % 3) * 0.2,         // 페이스
                    30 + i,     // 고도
                    150 + random.nextInt(10),    // 케이던스
                    120 + random.nextInt(20),    // 심박수
                    i % 2 == 0                   // 달리는 중 여부
            );
            telemetryList.add(telemetry);
        }
    }
}
