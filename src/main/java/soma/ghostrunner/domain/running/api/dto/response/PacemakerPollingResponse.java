package soma.ghostrunner.domain.running.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import soma.ghostrunner.domain.running.domain.Pacemaker;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PacemakerPollingResponse {

    private String processingStatus;
    private PacemakerResponse pacemaker;

    @Builder
    @Getter
    @AllArgsConstructor @NoArgsConstructor
    public static class PacemakerResponse {

        private Long id;
        private Pacemaker.Norm norm;
        private String summary;
        private Double goalKm;
        private Integer expectedMinutes;
        private String initialMessage;
        private Long runningId;
        private List<PacemakerSetResponse> sets;

    }

    @Builder
    @Getter
    @AllArgsConstructor @NoArgsConstructor
    public static class PacemakerSetResponse {

        private Integer setNum;
        private String message;
        private Double startPoint;
        private Double endPoint;
        private Double pace;

    }

}
