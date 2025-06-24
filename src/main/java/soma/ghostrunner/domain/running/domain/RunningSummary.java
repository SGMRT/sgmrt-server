package soma.ghostrunner.domain.running.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunningSummary {
    @Lob @Column(name = "running_summary", columnDefinition = "TEXT")
    private String summary;

    @Builder
    private RunningSummary(String summary) {
        this.summary = summary;
    }

    public static RunningSummary of(String summary) {
        return RunningSummary.builder().summary(summary).build();
    }
}
