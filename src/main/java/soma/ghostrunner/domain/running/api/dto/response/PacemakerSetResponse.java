package soma.ghostrunner.domain.running.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter @Builder
@AllArgsConstructor @NoArgsConstructor
public class PacemakerSetResponse {

    private Integer setNum;
    private String message;
    private Double startPoint;
    private Double endPoint;
    private Double pace;

}
