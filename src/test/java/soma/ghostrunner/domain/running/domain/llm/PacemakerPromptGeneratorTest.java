package soma.ghostrunner.domain.running.domain.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.member.domain.Gender;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.application.dto.WorkoutDto;
import soma.ghostrunner.domain.running.application.dto.WorkoutSetDto;
import soma.ghostrunner.domain.running.domain.RunningType;
import soma.ghostrunner.domain.running.domain.formula.WorkoutType;

import java.util.List;

class PacemakerPromptGeneratorTest {

    @DisplayName("훈련표를 최적화하기 위한 프롬프트를 생성한다.")
    @Test
    void generateWorkoutImprovementPrompt() {
        // given
        Member member = Member.of("이복둥", "프로필 URL");
        member.updateBioInfo(Gender.MALE, 28, 60, 170);

        WorkoutSetDto s1 = WorkoutSetDto.of(1, WorkoutType.E, "5:10", 0.0, 1.5);
        WorkoutSetDto s2 = WorkoutSetDto.of(2, WorkoutType.E, "5:05", 1.5, 7.5);
        WorkoutSetDto s3 = WorkoutSetDto.of(2, WorkoutType.E, "7:30", 7.5, 8.5);
        WorkoutSetDto s4 = WorkoutSetDto.of(3, WorkoutType.E, "4:40", 8.5, 10.0);

        WorkoutDto dto = WorkoutDto.of(RunningType.E, 10.0, List.of(s1, s2, s3, s4));

        // when // then
        String prompt = PacemakerPromptGenerator.generateWorkoutImprovementPrompt(member, 35, 2, 30, dto);
        System.out.println(prompt);
    }

    @DisplayName("음성 안내를 만들기 위한 프롬프트를 생성한다.")
    @Test
    void generateVoiceGuidancePrompt() {
        // given
        Member member = Member.of("이복둥", "프로필 URL");
        member.updateBioInfo(Gender.MALE, 28, 60, 170);

        WorkoutSetDto s1 = WorkoutSetDto.of(1, WorkoutType.E, "5:10", 0.0, 1.5, "피드백1");
        WorkoutSetDto s2 = WorkoutSetDto.of(2, WorkoutType.E, "5:05", 1.5, 7.5, "피드백2");
        WorkoutSetDto s3 = WorkoutSetDto.of(2, WorkoutType.E, "7:30", 7.5, 8.5, "피드백3");
        WorkoutSetDto s4 = WorkoutSetDto.of(3, WorkoutType.E, "4:40", 8.5, 10.0, "피드백4");

        WorkoutDto dto = WorkoutDto.of(RunningType.E, 10.0, List.of(s1, s2, s3, s4), 50);

        // when // then
        String prompt = PacemakerPromptGenerator.generateVoiceGuidancePrompt(member, 35, 2, 30, dto);
        System.out.println(prompt);
    }

}
