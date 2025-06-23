package soma.ghostrunner.domain.course;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.course.domain.CourseExtractor;
import soma.ghostrunner.domain.course.domain.StartPoint;
import soma.ghostrunner.domain.running.application.dto.TelemetryCommand;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class CourseExtractorTest {

    private final List<TelemetryCommand> telemetryList = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Random random = new Random();
        LocalDateTime baseTime = LocalDateTime.now();

        for (int i = 0; i < 10; i++) {
            TelemetryCommand telemetry = new TelemetryCommand(
                    baseTime.plusSeconds(i * 5), // 5초 간격
                    37.5665 + i * 0.0001,         // 위도
                    126.9780 + i * 0.0001,        // 경도
                    i * 10.0,                    // 거리 (예: 10m 단위)
                    5.0 + (i % 3) * 0.2,         // 페이스
                    30 + random.nextInt(20),     // 고도
                    150 + random.nextInt(10),    // 케이던스
                    120 + random.nextInt(20),    // 심박수
                    i % 2 == 0                   // 달리는 중 여부
            );
            telemetryList.add(telemetry);
        }
    }

    @DisplayName("시계열에서 시작점 추출 테스트")
    @Test
    void extractStartPoint() {
        // given
        StartPoint startPoint = CourseExtractor.extractStartPoint(telemetryList);

        // then
        Assertions.assertThat(startPoint.getLatitude()).isEqualTo(37.5665);
        Assertions.assertThat(startPoint.getLongitude()).isEqualTo(126.9780);
    }

    @DisplayName("시계열에서 위경도 추출 테스트")
    @Test
    void extractCourseCoordinates() {
        // given
        String coordinates = CourseExtractor.extractCourseCoordinates(telemetryList);

        // then
        System.out.println(coordinates);
    }
}
