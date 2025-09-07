package soma.ghostrunner.domain.running.domain.llm;

public interface PacemakerLlmClient {

    void requestLlmToCreatePacemaker(String prompt);

    void improveWorkout(String prompt);

    void fillVoiceGuidance(String prompt);

}
