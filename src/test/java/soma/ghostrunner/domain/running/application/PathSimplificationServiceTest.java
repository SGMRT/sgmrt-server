package soma.ghostrunner.domain.running.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.running.domain.path.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class PathSimplificationServiceUnitTest {

    PathSimplificationService sut;

    @Mock
    TelemetryStatistics stats; // relativeTelemetries()만 쓰므로 mock으로 충분
    // Telemetry/Coordinates/Checkpoint는 실제 프로젝트의 패키지 기준으로 import

    @BeforeEach
    void setUp() {
        sut = new PathSimplificationService();
    }

    private Telemetry t(long ts, double lat, double lng) {
        // Telemetry(t, y(lat), x(lng), d,p,e,c,b,r)
        return new Telemetry(ts, lat, lng, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("정상 흐름: 변환된 좌표 리스트를 정적 유틸들에 넘겨 오케스트레이션한다")
    void simplify_success_orchestration() {
        // given: Telemetry → CoordinatesWithTs 로 변환될 원본
        var telemetries = Arrays.asList(
                t(0,   37.0, 127.0),
                t(100, 37.0005, 127.0005),
                t(200, 37.0010, 127.0010)
        );
        when(stats.relativeTelemetries()).thenReturn(telemetries);

        // expected outputs from PathSimplifier
        var simplified = Arrays.asList(
                new Coordinates(37.0, 127.0),
                new Coordinates(37.0010, 127.0010)
        );
        var edgePoints = Arrays.asList(
                new Coordinates(37.0, 127.0),
                new Coordinates(37.0005, 127.0005),
                new Coordinates(37.0010, 127.0010)
        );
        var checkpoints = Arrays.asList(
                new Checkpoint(37.0, 127.0, 0),
                new Checkpoint(37.0005, 127.0005, 45),
                new Checkpoint(37.0010, 127.0010, null)
        );

        // when: 정적 유틸 모킹
        try (MockedStatic<PathSimplifier> ps = mockStatic(PathSimplifier.class)) {

            // simplifyToRenderingTelemetries: 전달받은 points가 변환 결과와 동일한지 내부에서 검증
            ps.when(() -> PathSimplifier.simplifyToRenderingTelemetries(anyList()))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        List<CoordinatesWithTs> arg = (List<CoordinatesWithTs>) invocation.getArgument(0);
                        // 변환 기대값 계산
                        List<CoordinatesWithTs> expected =
                                CoordinatesWithTs.toCoordinatesWithTsList(telemetries);

                        assertThat(arg).hasSameSizeAs(expected);
                        for (int i = 0; i < expected.size(); i++) {
                            assertThat(arg.get(i).getT()).isEqualTo(expected.get(i).getT());
                            assertThat(arg.get(i).getY()).isEqualTo(expected.get(i).getY());
                            assertThat(arg.get(i).getX()).isEqualTo(expected.get(i).getX());
                        }
                        return simplified;
                    });

            // extractEdgePoints: 동일하게 변환 리스트가 전달되는지 확인
            ps.when(() -> PathSimplifier.extractEdgePoints(anyList()))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        List<CoordinatesWithTs> arg = (List<CoordinatesWithTs>) invocation.getArgument(0);
                        List<CoordinatesWithTs> expected =
                                CoordinatesWithTs.toCoordinatesWithTsList(telemetries);

                        assertThat(arg).hasSameSizeAs(expected);
                        return edgePoints; // 이후 calculateAngles 입력으로 사용됨
                    });

            // calculateAngles: edgePoints가 그대로 넘어오는지까진 여기서 보장 어려움 → 서비스는 오케스트레이션만
            ps.when(() -> PathSimplifier.calculateAngles(edgePoints))
                    .thenReturn(checkpoints);

            // execute
            SimplifiedPaths result = sut.simplify(stats);

            // then
            assertThat(result).isNotNull();
            assertThat(result.simplifiedCoordinates()).containsExactlyElementsOf(simplified);
            assertThat(result.checkpoints()).containsExactlyElementsOf(checkpoints);

            // 호출 여부 검증
            ps.verify(() -> PathSimplifier.simplifyToRenderingTelemetries(anyList()), times(1));
            ps.verify(() -> PathSimplifier.extractEdgePoints(anyList()), times(1));
            ps.verify(() -> PathSimplifier.calculateAngles(edgePoints), times(1));
        }
    }

    @Test
    @DisplayName("빈 입력: 정적 유틸이 빈 결과를 반환하면 그대로 래핑해 돌려준다")
    void simplify_emptyInput_returnsEmptyWrapped() {
        // given
        when(stats.relativeTelemetries()).thenReturn(Collections.emptyList());

        var simplified = Collections.<Coordinates>emptyList();
        var edgePoints = Collections.<Coordinates>emptyList();
        var checkpoints = Collections.<Checkpoint>emptyList();

        try (MockedStatic<PathSimplifier> ps = mockStatic(PathSimplifier.class)) {
            ps.when(() -> PathSimplifier.simplifyToRenderingTelemetries(anyList()))
                    .thenReturn(simplified);
            ps.when(() -> PathSimplifier.extractEdgePoints(anyList()))
                    .thenReturn(edgePoints);
            ps.when(() -> PathSimplifier.calculateAngles(edgePoints))
                    .thenReturn(checkpoints);

            // when
            SimplifiedPaths result = sut.simplify(stats);

            // then
            assertThat(result.simplifiedCoordinates()).isEmpty();
            assertThat(result.checkpoints()).isEmpty();

            ps.verify(() -> PathSimplifier.simplifyToRenderingTelemetries(anyList()), times(1));
            ps.verify(() -> PathSimplifier.extractEdgePoints(anyList()), times(1));
            ps.verify(() -> PathSimplifier.calculateAngles(edgePoints), times(1));
        }
    }
}
