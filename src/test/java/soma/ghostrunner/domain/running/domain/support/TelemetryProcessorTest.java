package soma.ghostrunner.domain.running.domain.support;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.running.domain.path.TelemetryStatistics;
import soma.ghostrunner.domain.running.domain.path.Telemetry;
import soma.ghostrunner.domain.running.domain.path.TelemetryProcessor;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.domain.running.exception.TelemetryCalculationException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@SpringBootTest
class TelemetryProcessorTest extends IntegrationTestSupport {

    @Autowired
    TelemetryProcessor telemetryProcessor;

    @DisplayName(".jsonl Multipart 파일을 역직렬화 후 상대 시간 변환, 좌표 수집, 최고 / 최저 속도, 평균 고도, 총 전체 거리를 계산한다.")
    @Test
    void processTelemetryTest() throws Exception {
        // given
        Long startedAt = 1750729987181L;
        List<Telemetry> telemetryList = getTelemetryDtos(startedAt);
        MultipartFile multipartTelemetryList = createTelemetryJsonlFile(telemetryList);

        // when
        TelemetryStatistics processedTelemetry = telemetryProcessor.process(multipartTelemetryList, startedAt);

        // then
        for (int i = 0; i < telemetryList.size(); i++) {
            Assertions.assertThat(processedTelemetry.relativeTelemetries().get(i).getT()).isEqualTo(i*5);
            Assertions.assertThat(processedTelemetry.relativeTelemetries().get(i).getY()).isEqualTo(telemetryList.get(i).getY());
            Assertions.assertThat(processedTelemetry.relativeTelemetries().get(i).getX()).isEqualTo(telemetryList.get(i).getX());
        }

        Assertions.assertThat(processedTelemetry.startPoint().y()).isEqualTo(37.5665);
        Assertions.assertThat(processedTelemetry.startPoint().x()).isEqualTo(126.9780);

        Assertions.assertThat(processedTelemetry.lowestPace()).isEqualTo(5.0);
        Assertions.assertThat(processedTelemetry.highestPace()).isEqualTo(14.0);
        Assertions.assertThat(processedTelemetry.avgElevation()).isEqualTo(4.5);

        System.out.println(processedTelemetry.courseDistance());
    }

    @DisplayName(".jsonl Multipart 파일이 비어있다면 예외를 발생한다.")
    @Test
    void processEmptyTelemetryTest() throws Exception {
        // given
        Long startedAt = 1750729987181L;
        List<Telemetry> telemetryList = new ArrayList<>();
        MultipartFile multipartTelemetryList = createTelemetryJsonlFile(telemetryList);

        // when // then
        Assertions.assertThatThrownBy(() -> telemetryProcessor.process(multipartTelemetryList, startedAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Telemetry data is empty.");
    }

    private List<Telemetry> getTelemetryDtos(Long startedAt) {
        List<Telemetry> telemetryList = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            Telemetry telemetry = new Telemetry(
                    startedAt + (i*5),     // 5초 간격
                    37.5665 + i * 0.0001,         // 위도
                    126.9780 + i * 0.0001,        // 경도
                    i * 10.0,                    // 거리 (예: 10m 단위)
                    5.0 + i,         // 페이스
                    30.0 + i,     // 고도
                    150 + random.nextInt(10),    // 케이던스
                    120 + random.nextInt(20),    // 심박수
                    i % 2 == 0                   // 달리는 중 여부
            );
            telemetryList.add(telemetry);
        }
        return telemetryList;
    }

    private MultipartFile createTelemetryJsonlFile(List<Telemetry> telemetryList) throws Exception {

        ObjectMapper objectMapper = new ObjectMapper();

        StringBuilder sb = new StringBuilder();
        for (Telemetry dto : telemetryList) {
            sb.append(objectMapper.writeValueAsString(dto)).append("\n");
        }

        return new MockMultipartFile(
                "file",
                "telemetry.jsonl",
                "application/json",
                sb.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    @DisplayName("BPM, Pace, Cadence, Distance 중 마이너스 값이 있다면 예외를 발생한다.")
    @Test
    void processMinusTelemetryTest() throws Exception {
        // given
        Long startedAt = 1750729987181L;
        List<Telemetry> telemetryList = createMinusTelemetryDtos(startedAt);
        MultipartFile multipartTelemetryList = createTelemetryJsonlFile(telemetryList);

        // when // then
        Assertions.assertThatThrownBy(() -> telemetryProcessor.process(multipartTelemetryList, startedAt))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("마이너스 값이 포함되어 있습니다.");
    }

    private List<Telemetry> createMinusTelemetryDtos(Long startedAt) {
        List<Telemetry> telemetryList = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            Telemetry telemetry = new Telemetry(
                    startedAt + (i*5),     // 5초 간격
                    37.5665 + i * 0.0001,         // 위도
                    126.9780 + i * 0.0001,        // 경도
                    i * 10.0,                    // 거리 (예: 10m 단위)
                    - 5.0 + i,         // 페이스
                    30.0 + i,     // 고도
                    - 150 + random.nextInt(10),    // 케이던스
                    120 + random.nextInt(20),    // 심박수
                    i % 2 == 0                   // 달리는 중 여부
            );
            telemetryList.add(telemetry);
        }
        return telemetryList;
    }

    @DisplayName("필드값이 유효하지 않은 .jsonl 파일이라면 예외를 발생한다.")
    @Test
    void testProcessInvalidTelemetryTest() throws Exception {
        // given
        Long startedAt = 1750729987181L;
        List<InvalidTelemetryDto> invalidTelemetryDtos = createInvalidTelemetryDtos(startedAt);
        MultipartFile multipartTelemetryList = createInvalidTelemetryJsonlFile(invalidTelemetryDtos);

        // when // then
        Assertions.assertThatThrownBy(() -> telemetryProcessor.process(multipartTelemetryList, startedAt))
                .isInstanceOf(TelemetryCalculationException.class)
                .hasMessage("시계열 좌표를 가공하는 중 에러가 발생했습니다.");
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    private class InvalidTelemetryDto {

        private Long timeStamp;
        private Double invalidLat;
        private Double invalidLng;
        private Double dist;
        private Double pace;
        private Double alt;
        private Integer cadence;
        private Integer bpm;
        private Boolean isRunning;

    }

    private List<InvalidTelemetryDto> createInvalidTelemetryDtos(Long startedAt) {
        List<InvalidTelemetryDto> invalidTelemetryDtos = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            InvalidTelemetryDto invalidTelemetryDto = new InvalidTelemetryDto(
                    startedAt + (i*5),     // 5초 간격
                    37.5665 + i * 0.0001,         // 위도
                    126.9780 + i * 0.0001,        // 경도
                    i * 10.0,                    // 거리 (예: 10m 단위)
                    - 5.0 + i,         // 페이스
                    30.0 + i,     // 고도
                    - 150 + random.nextInt(10),    // 케이던스
                    120 + random.nextInt(20),    // 심박수
                    i % 2 == 0                   // 달리는 중 여부
            );
            invalidTelemetryDtos.add(invalidTelemetryDto);
        }
        return invalidTelemetryDtos;
    }

    private MultipartFile createInvalidTelemetryJsonlFile(List<InvalidTelemetryDto> telemetryList) throws Exception {

        ObjectMapper objectMapper = new ObjectMapper();

        StringBuilder sb = new StringBuilder();
        for (InvalidTelemetryDto dto : telemetryList) {
            sb.append(objectMapper.writeValueAsString(dto)).append("\n");
        }

        return new MockMultipartFile(
                "file",
                "invalid_telemetry.jsonl",
                "application/json",
                sb.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

}
