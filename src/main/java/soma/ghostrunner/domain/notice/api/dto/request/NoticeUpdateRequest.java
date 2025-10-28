package soma.ghostrunner.domain.notice.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticeUpdateRequest {

    @NotEmpty
    private String title;

    private String content;

    private Integer priority;

    private MultipartFile image;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startAt;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endAt;

    @Builder.Default
    private Set<UpdateAttrs> updateAttrs = Set.of();

    public enum UpdateAttrs {
        TITLE,
        CONTENT,
        PRIORITY,
        IMAGE,
        START_AT,
        END_AT
    }

    @Override
    public String toString() {
        return String.format("updateAttrs=%s, title=%s, content=%s, priority=%s, image=%s, startAt=%s, endAt=%s",
                updateAttrs, title, content, priority, image, startAt, endAt);
    }

}