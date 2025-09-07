package soma.ghostrunner.domain.running.application.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.running.domain.RunningType;
import soma.ghostrunner.domain.running.domain.formula.WorkoutType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessedWorkoutDtoTest {

    @DisplayName("페이스메이커 프롬프트를 만들기 위해 Json 형태의 String으로 변환한다.")
    @Test
    void toStringForPacemakerPrompt() {
        // given
        ProcessedWorkoutSetDto s1 = ProcessedWorkoutSetDto.of(1, WorkoutType.E, "5:10", 0.0, 1.5);
        ProcessedWorkoutSetDto s2 = ProcessedWorkoutSetDto.of(2, WorkoutType.E, "5:05", 1.5, 7.5);
        ProcessedWorkoutSetDto s3 = ProcessedWorkoutSetDto.of(2, WorkoutType.E, "7:30", 7.5, 8.5);
        ProcessedWorkoutSetDto s4 = ProcessedWorkoutSetDto.of(3, WorkoutType.E, "4:40", 8.5, 10.0);

        ProcessedWorkoutDto dto = ProcessedWorkoutDto.of(RunningType.E, 10.0, List.of(s1, s2, s3, s4));

        // when
        String json = dto.toStringForPacemakerPrompt();
        System.out.println(json);

        // 간단 검증
        assertTrue(json.contains("\"type\": \"E\""));
        assertTrue(json.contains("\"goal_km\": 10.0"));
        assertTrue(json.contains("\"sets\""));
        assertTrue(json.contains("\"pace_min/km\": \"5:10\""));
        assertTrue(json.contains("\"end_km\": 10.0"));
    }

}
