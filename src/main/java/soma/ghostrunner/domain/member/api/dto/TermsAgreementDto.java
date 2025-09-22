package soma.ghostrunner.domain.member.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class TermsAgreementDto {

    @NotNull
    private boolean serviceTermsAgreed;                     // 서비스 이용약관 (필수)

    @NotNull
    private boolean privacyPolicyAgreed;                    // 개인정보 수집 및 동의 (필수)

    @NotNull
    private boolean personalInformationUsageConsentAgreed;  // 개인정보 수집 및 이용 동의 (필수)

    private LocalDateTime agreedAt;

}
