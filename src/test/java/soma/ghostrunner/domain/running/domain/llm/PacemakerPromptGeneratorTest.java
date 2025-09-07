package soma.ghostrunner.domain.running.domain.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.member.domain.Gender;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.application.dto.ProcessedWorkoutDto;
import soma.ghostrunner.domain.running.application.dto.ProcessedWorkoutSetDto;
import soma.ghostrunner.domain.running.domain.RunningType;
import soma.ghostrunner.domain.running.domain.formula.WorkoutType;

import java.util.List;

class PacemakerPromptGeneratorTest {

    @DisplayName("페이스메이커를 만들기 위한 프롬프트를 생성한다.")
    @Test
    void generateWorkoutImprovementPrompt() {
        // given
        Member member = Member.of("이복둥", "프로필 URL");
        member.updateBioInfo(Gender.MALE, 28, 60, 170);

        ProcessedWorkoutSetDto s1 = ProcessedWorkoutSetDto.of(1, WorkoutType.E, "5:10", 0.0, 1.5);
        ProcessedWorkoutSetDto s2 = ProcessedWorkoutSetDto.of(2, WorkoutType.E, "5:05", 1.5, 7.5);
        ProcessedWorkoutSetDto s3 = ProcessedWorkoutSetDto.of(2, WorkoutType.E, "7:30", 7.5, 8.5);
        ProcessedWorkoutSetDto s4 = ProcessedWorkoutSetDto.of(3, WorkoutType.E, "4:40", 8.5, 10.0);

        ProcessedWorkoutDto dto = ProcessedWorkoutDto.of(RunningType.E, 10.0, List.of(s1, s2, s3, s4));

        // when // then
        String prompt = PacemakerPromptGenerator.generateWorkoutImprovementPrompt(member, 35, 2, 30, dto);
        System.out.println(prompt);
     }

}
