package soma.ghostrunner.domain.auth.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import org.hibernate.validator.constraints.URL;
import soma.ghostrunner.domain.auth.api.dto.TermsAgreementDto;
import soma.ghostrunner.domain.member.enums.Gender;

@Getter
@AllArgsConstructor
public class SignUpRequest {
    @NotBlank
    private String nickname;

    @URL
    private String profileImageUrl;

    @NotNull
    private Gender gender;

    @Positive
    private Integer height;

    @Positive
    private Integer weight;

    @NotNull
    private TermsAgreementDto agreement;
}
