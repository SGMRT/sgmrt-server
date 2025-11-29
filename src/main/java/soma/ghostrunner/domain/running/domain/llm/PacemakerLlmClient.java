package soma.ghostrunner.domain.running.domain.llm;

public interface PacemakerLlmClient {

    String improveWorkout(String prompt);

    String fillVoiceGuidance(String prompt);

}
