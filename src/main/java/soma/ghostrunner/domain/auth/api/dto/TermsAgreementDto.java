package soma.ghostrunner.domain.auth.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class TermsAgreementDto {
    private boolean serviceTermsAgreed;           // 서비스 이용약관 (필수)
    private boolean privacyPolicyAgreed;          // 개인정보 수집 및 동의 (필수)
    private boolean dataConsignmentAgreed;      // 개인정보처리 위탁 동의 (필수)
    private boolean thirdPartyDataSharingAgreed; // 개인정보 제3자 제공 동의 (필수)
    private boolean marketingAgreed;              // 마케팅 정보 수신 동의 (선택)
    private LocalDateTime agreedAt;
}
