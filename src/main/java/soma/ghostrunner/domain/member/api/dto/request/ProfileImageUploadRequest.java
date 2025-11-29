package soma.ghostrunner.domain.member.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProfileImageUploadRequest {
    @NotBlank
    private String filename;
//    @EnumValid(enumClass = AllowedProfileImageContentType.class, message = "유효하지 않은 Content-Type입니다.")
    @NotBlank
    private String contentType;
}
