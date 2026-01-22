package soma.ghostrunner.domain.pacemaker.domain.llm;

public interface PacemakerLlmClient {

    String improveWorkout(String prompt);

    String fillVoiceGuidance(String prompt);

}
