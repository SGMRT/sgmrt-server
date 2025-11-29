package soma.ghostrunner.domain.notice.api.dto.request;


import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.domain.notice.domain.enums.NoticeType;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticeCreationRequest {

    @NotEmpty
    private String title;

    private String content;

    private NoticeType type;

    private Integer priority;

    private MultipartFile image;

}
