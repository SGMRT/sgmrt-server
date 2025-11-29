package soma.ghostrunner.domain.running.infra.openai;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OpenAiRequest {

    private String model;
    private String input;

}
