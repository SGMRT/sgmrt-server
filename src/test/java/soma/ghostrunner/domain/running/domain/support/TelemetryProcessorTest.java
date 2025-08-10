package soma.ghostrunner.domain.running.domain.support;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import soma.ghostrunner.domain.running.application.dto.ProcessedTelemetriesDto;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.domain.running.application.support.TelemetryProcessor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class TelemetryProcessorTest {

    @DisplayName(".jsonl Multipart 파일을 역직렬화 후 상대 시간 변환, 좌표 수집, 최고 / 최저 속도를 계산한다.")
    @Test
    void processTelemetryTest() throws Exception {
        // given
        Long startedAt = 1750729987181L;
        List<TelemetryDto> telemetryList = getTelemetryDtos(startedAt);
        MultipartFile multipartTelemetryList = createTelemetryJsonlFile(telemetryList);

        // when
        ProcessedTelemetriesDto processedTelemetry = TelemetryProcessor.process(multipartTelemetryList);

        // then
        for (int i = 0; i < telemetryList.size(); i++) {
            Assertions.assertThat(processedTelemetry.relativeTelemetries().get(i).getTimeStamp()).isEqualTo(i);
            Assertions.assertThat(processedTelemetry.coordinates().get(i).lat()).isEqualTo(telemetryList.get(i).getLat());
            Assertions.assertThat(processedTelemetry.coordinates().get(i).lng()).isEqualTo(telemetryList.get(i).getLng());
        }

        Assertions.assertThat(processedTelemetry.startPoint().lat()).isEqualTo(37.5665);
        Assertions.assertThat(processedTelemetry.startPoint().lng()).isEqualTo(126.9780);

        Assertions.assertThat(processedTelemetry.lowestPace()).isEqualTo(5.0);
        Assertions.assertThat(processedTelemetry.highestPace()).isEqualTo(14.0);
    }

    private List<TelemetryDto> getTelemetryDtos(Long startedAt) {
        List<TelemetryDto> telemetryList = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            TelemetryDto telemetry = new TelemetryDto(
                    startedAt + (i*5),     // 5초 간격
                    37.5665 + i * 0.0001,         // 위도
                    126.9780 + i * 0.0001,        // 경도
                    i * 10.0,                    // 거리 (예: 10m 단위)
                    5.0 + i,         // 페이스
                    30 + i,     // 고도
                    150 + random.nextInt(10),    // 케이던스
                    120 + random.nextInt(20),    // 심박수
                    i % 2 == 0                   // 달리는 중 여부
            );
            telemetryList.add(telemetry);
        }
        return telemetryList;
    }

    private MultipartFile createTelemetryJsonlFile(List<TelemetryDto> telemetryList) throws Exception {

        ObjectMapper objectMapper = new ObjectMapper();

        StringBuilder sb = new StringBuilder();
        for (TelemetryDto dto : telemetryList) {
            sb.append(objectMapper.writeValueAsString(dto)).append("\n");
        }

        return new MockMultipartFile(
                "file",
                "telemetry.jsonl",
                "application/json",
                sb.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

}
