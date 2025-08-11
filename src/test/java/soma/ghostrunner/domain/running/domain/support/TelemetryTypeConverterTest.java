package soma.ghostrunner.domain.running.domain.support;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.domain.running.application.support.TelemetryTypeConverter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.tuple;

class TelemetryTypeConverterTest {

    @DisplayName("시계열 DTO 객체리스트를 문자로 변환한다.")
    @Test
    void convertFromObjectsToString() {
        // given
        List<TelemetryDto> telemetryDtos = createTelemetryDtos();

        // when
        String result = TelemetryTypeConverter.convertFromObjectsToString(telemetryDtos);

        // then
        String[] firstTelemetries = result.split("\n")[0].split(",");
        String ts = firstTelemetries[0].split(":")[1];
        String lat = firstTelemetries[1].split(":")[1];

        Assertions.assertThat(ts).isEqualTo(String.valueOf(telemetryDtos.get(0).getTimeStamp()));
        Assertions.assertThat(lat).isEqualTo(String.valueOf(telemetryDtos.get(0).getLat()));
    }

    private List<TelemetryDto> createTelemetryDtos() {
        List<TelemetryDto> telemetryDtos = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            telemetryDtos.add(new TelemetryDto(100L + i, 36.2 + i, 37.3 + i, 10.1 + i,
                    6.4 + i, 110.0 + i, 120 + i, 110 + i, true));
        }
        return telemetryDtos;
    }

    @DisplayName("시계열 문자를 DTO 객체리스트로 변환한다.")
    @Test
    void convertFromStringToDtos() {
        // given
        List<String> stringTelemetries = new ArrayList<>();
        stringTelemetries.add("{\"timeStamp\":13,\"lat\":37.2,\"lng\":35.1,\"dist\":0.1,\"pace\":6.0,\"alt\":10,\"cadence\":120,\"bpm\":110,\"isRunning\":true}");
        stringTelemetries.add("{\"timeStamp\":14,\"lat\":37.3,\"lng\":35.2,\"dist\":0.2,\"pace\":6.1,\"alt\":11,\"cadence\":121,\"bpm\":111,\"isRunning\":true}");
        stringTelemetries.add("{\"timeStamp\":15,\"lat\":37.4,\"lng\":35.3,\"dist\":0.3,\"pace\":6.2,\"alt\":12,\"cadence\":122,\"bpm\":112,\"isRunning\":true}");
        stringTelemetries.add("{\"timeStamp\":16,\"lat\":37.5,\"lng\":35.4,\"dist\":0.4,\"pace\":6.3,\"alt\":13,\"cadence\":123,\"bpm\":113,\"isRunning\":true}");

        // when
        List<TelemetryDto> result = TelemetryTypeConverter.convertFromStringToDtos(stringTelemetries);

        // then
        Assertions.assertThat(result)
                .hasSize(4)
                .extracting("timeStamp", "lat", "lng", "dist", "pace", "alt", "cadence", "bpm", "isRunning")
                .containsExactlyInAnyOrder(
                        tuple(13L, 37.2, 35.1, 0.1, 6.0, 10, 120, 110, true),
                        tuple(14L, 37.3, 35.2, 0.2, 6.1, 11, 121, 111, true),
                        tuple(15L, 37.4, 35.3, 0.3, 6.2, 12, 122, 112, true),
                        tuple(16L, 37.5, 35.4, 0.4, 6.3, 13, 123, 113, true)
                        );
    }

}
