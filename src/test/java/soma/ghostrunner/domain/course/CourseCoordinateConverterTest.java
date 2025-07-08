package soma.ghostrunner.domain.course;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.running.application.dto.CourseCoordinateDto;
import soma.ghostrunner.domain.running.domain.support.CourseCoordinateConverter;

import java.util.List;

import static org.assertj.core.api.Assertions.tuple;

class CourseCoordinateConverterTest {

    @DisplayName("러닝 시계열에서 위도/경도를 추출한다.")
    @Test
    void convertToCoordinateList() {
        // given
        List<String> stringTelemetries = List.of(
                "{\"timeStamp\":0,\"lat\":37.1,\"lng\":37.5,\"dist\":110.0,\"pace\":6.0,\"alt\":110,\"cadence\":120,\"bpm\":130,\"isRunning\":true}",
                "{\"timeStamp\":1,\"lat\":37.2,\"lng\":37.6,\"dist\":118.0,\"pace\":6.1,\"alt\":130,\"cadence\":121,\"bpm\":130,\"isRunning\":true}",
                "{\"timeStamp\":2,\"lat\":37.3,\"lng\":37.7,\"dist\":118.0,\"pace\":6.1,\"alt\":130,\"cadence\":121,\"bpm\":130,\"isRunning\":true}",
                "{\"timeStamp\":3,\"lat\":37.4,\"lng\":37.8,\"dist\":118.0,\"pace\":6.1,\"alt\":130,\"cadence\":121,\"bpm\":130,\"isRunning\":true}",
                "{\"timeStamp\":4,\"lat\":37.5,\"lng\":37.9,\"dist\":118.0,\"pace\":6.1,\"alt\":130,\"cadence\":121,\"bpm\":130,\"isRunning\":true}"
        );

        // when
        List<CourseCoordinateDto> courseCoordinateDtos = CourseCoordinateConverter.convertToCoordinateList(stringTelemetries);

        // then
        Assertions.assertThat(courseCoordinateDtos)
                .hasSize(5)
                .extracting("lat", "lng")
                .containsExactly(
                        tuple(37.1, 37.5),
                        tuple(37.2, 37.6),
                        tuple(37.3, 37.7),
                        tuple(37.4, 37.8),
                        tuple(37.5, 37.9)
                );

     }

}
