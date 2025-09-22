package soma.ghostrunner.domain.running.domain.llm;

import reactor.core.publisher.Mono;

public interface PacemakerLlmClient {

    Mono<String> improveWorkout(String prompt);

    Mono<String> fillVoiceGuidance(String prompt);

}
