package soma.ghostrunner.domain.running.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Data @Builder
@AllArgsConstructor @NoArgsConstructor
public class DeleteRunningRequest {

    @NotEmpty
    private List<Long> runningIds;

}
