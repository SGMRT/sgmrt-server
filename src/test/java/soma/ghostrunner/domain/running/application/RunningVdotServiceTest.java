package soma.ghostrunner.domain.running.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningType;
import soma.ghostrunner.domain.running.domain.formula.VdotCalculator;
import soma.ghostrunner.domain.running.domain.formula.VdotPace;
import soma.ghostrunner.domain.running.domain.formula.VdotPaceProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RunningVdotServiceTest {

    @Mock
    VdotCalculator vdotCalculator;
    @Mock
    VdotPaceProvider vdotPaceProvider;

    RunningVdotService runningVdotService;

    @BeforeEach
    void setUp() {
        runningVdotService = new RunningVdotService(vdotCalculator, vdotPaceProvider);
    }

    @Test
    @DisplayName("calculateVdot: 평균 페이스를 1마일 페이스로 변환해 계산기에 전달한다")
    void calculateVdot_callsCalculatorWithConvertedPace() {
        // given
        double averagePacePerKm = 6.20; // 예시 값(분/킬로)
        double oneMilePace = 9.99;      // 변환 결과를 고정 (정적 모킹)

        try (MockedStatic<Running> runningStatic = mockStatic(Running.class)) {
            runningStatic.when(() -> Running.calculateOneMilePace(averagePacePerKm))
                    .thenReturn(oneMilePace);

            when(vdotCalculator.calculateFromPace(oneMilePace)).thenReturn(41);

            // when
            int vdot = runningVdotService.calculateVdot(averagePacePerKm);

            // then
            assertThat(vdot).isEqualTo(41);
            runningStatic.verify(() -> Running.calculateOneMilePace(averagePacePerKm), times(1));
            verify(vdotCalculator).calculateFromPace(oneMilePace);
            verifyNoMoreInteractions(vdotCalculator);
        }
    }

    @Test
    @DisplayName("getExpectedPacesByVdot: 타입별 페이스를 맵으로 반환(중복 키는 마지막 값 채택)")
    void getExpectedPacesByVdot_buildsTypeToPaceMap() {
        // given
        int vdot = 41;
        var list = List.of(
                new VdotPace(RunningType.E, 6.10),
                new VdotPace(RunningType.M, 5.22),
                new VdotPace(RunningType.T, 5.00),
                new VdotPace(RunningType.I, 4.36),
                new VdotPace(RunningType.R, 4.15),
                // 중복 키(E) 케이스: 마지막 값이 최종 반영되는지 확인
                new VdotPace(RunningType.E, 6.11)
        );
        when(vdotPaceProvider.getVdotPaceByVdot(vdot)).thenReturn(list);

        // when
        Map<RunningType, Double> map = runningVdotService.getExpectedPacesByVdot(vdot);

        // then
        assertThat(map).hasSize(5);
        assertThat(map.get(RunningType.E)).isEqualTo(6.11); // 마지막 값 채택
        assertThat(map.get(RunningType.M)).isEqualTo(5.22);
        assertThat(map.get(RunningType.T)).isEqualTo(5.00);
        assertThat(map.get(RunningType.I)).isEqualTo(4.36);
        assertThat(map.get(RunningType.R)).isEqualTo(4.15);

        verify(vdotPaceProvider).getVdotPaceByVdot(vdot);
        verifyNoMoreInteractions(vdotPaceProvider);
    }

    @DisplayName("러닝 레벨에 따라 1마일 페이스를 계산하고 VDOT를 반환한다")
    @Test
    void calculateVdotFromRunningLevel_success() {
        // given
        String runningLevel = "중급자";
        given(vdotCalculator.calculateFromPace(anyDouble())).willReturn(45);

        // when
        int vdot = runningVdotService.calculateVdotFromRunningLevel(runningLevel);

        // then
        assertThat(vdot).isEqualTo(45);
        verify(vdotCalculator).calculateFromPace(9.6);
    }

    @DisplayName("잘못된 러닝 레벨일 경우 Running 에서 예외가 발생한다.")
    @Test
    void calculateVdotFromRunningLevel_invalidLevel() {
        // given
        String invalidLevel = "초고수";

        // when // then
        assertThatThrownBy(() -> runningVdotService.calculateVdotFromRunningLevel(invalidLevel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("올바르지 않은 러닝 레벨입니다.");
    }

}
