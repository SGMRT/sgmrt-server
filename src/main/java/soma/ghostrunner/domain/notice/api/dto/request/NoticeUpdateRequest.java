package soma.ghostrunner.domain.notice.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NoticeUpdateRequest {

    @NotEmpty
    private String title;

    private String content;

    private Integer priority;

    private MultipartFile image;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private Set<UpdateAttrs> updateAttrs = Set.of();

    public enum UpdateAttrs {
        TITLE,
        CONTENT,
        PRIORITY,
        IMAGE,
        START_AT,
        END_AT
    }

}