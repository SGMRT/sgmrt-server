package soma.ghostrunner.domain.pacemaker.api.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PacemakerTimeTableResponseTest {

    @DisplayName("expectedMinutes가 null이면 maintenanceMinutes도 null이다")
    @Test
    void whenExpectedMinutesIsNull_maintenanceMinutesShouldBeNull() {
        // given
        List<PacemakerSetResponse> sets = List.of(
                PacemakerSetResponse.builder()
                        .setNum(1)
                        .startPoint(0.0)
                        .endPoint(1.0)
                        .pace(6.0)  // 6분/km
                        .build(),
                PacemakerSetResponse.builder()
                        .setNum(2)
                        .startPoint(1.0)
                        .endPoint(9.0)
                        .pace(5.0)
                        .build(),
                PacemakerSetResponse.builder()
                        .setNum(3)
                        .startPoint(9.0)
                        .endPoint(10.0)
                        .pace(6.0)  // 6분/km
                        .build()
        );
        Integer expectedMinutes = null;  // FALLBACK 상태에서 null 가능

        // when
        PacemakerTimeTableResponse response = new PacemakerTimeTableResponse(sets, expectedMinutes);

        // then
        assertThat(response.getWarmUpMinutes()).isEqualTo(6);   // 1km * 6분/km
        assertThat(response.getCoolDownMinutes()).isEqualTo(6); // 1km * 6분/km
        assertThat(response.getMaintenanceMinutes()).isNull();  // expectedMinutes가 null이므로
    }

    @DisplayName("expectedMinutes가 있으면 maintenanceMinutes를 정상 계산한다")
    @Test
    void whenExpectedMinutesIsPresent_maintenanceMinutesShouldBeCalculated() {
        // given
        List<PacemakerSetResponse> sets = List.of(
                PacemakerSetResponse.builder()
                        .setNum(1)
                        .startPoint(0.0)
                        .endPoint(1.0)
                        .pace(6.0)  // warmUp: 1km * 6분/km = 6분
                        .build(),
                PacemakerSetResponse.builder()
                        .setNum(2)
                        .startPoint(1.0)
                        .endPoint(9.0)
                        .pace(5.0)
                        .build(),
                PacemakerSetResponse.builder()
                        .setNum(3)
                        .startPoint(9.0)
                        .endPoint(10.0)
                        .pace(6.0)  // coolDown: 1km * 6분/km = 6분
                        .build()
        );
        Integer expectedMinutes = 50;  // 총 50분

        // when
        PacemakerTimeTableResponse response = new PacemakerTimeTableResponse(sets, expectedMinutes);

        // then
        assertThat(response.getWarmUpMinutes()).isEqualTo(6);
        assertThat(response.getCoolDownMinutes()).isEqualTo(6);
        assertThat(response.getMaintenanceMinutes()).isEqualTo(38);  // 50 - 6 - 6 = 38
    }

}
