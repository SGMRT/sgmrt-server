package soma.ghostrunner.domain.member.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private boolean isServiceTermsAgreed;                     // 서비스 이용 약관 (필수)
    private boolean isPrivacyPolicyAgreed;                    // 개인정보 처리 방침 (필수)
    private boolean isPersonalInformationUsageConsentAgreed;  // 개인정보 수집 및 이용 동의 (필수)

    @CreationTimestamp
    private LocalDateTime agreedAt;

    @Builder(access = AccessLevel.PRIVATE)
    public TermsAgreement(boolean isServiceTermsAgreed, boolean isPrivacyPolicyAgreed,
                          boolean isPersonalInformationUsageConsentAgreed, LocalDateTime agreedAt) {
        this.isServiceTermsAgreed = isServiceTermsAgreed;
        this.isPrivacyPolicyAgreed = isPrivacyPolicyAgreed;
        this.isPersonalInformationUsageConsentAgreed = isPersonalInformationUsageConsentAgreed;
        this.agreedAt = agreedAt;
    }

    public static TermsAgreement createIfAllMandatoryTermsAgreed(boolean isServiceTermsAgreed, boolean isPrivacyPolicyAgreed,
                                                                 boolean isPersonalInformationUsageConsentAgreed, LocalDateTime agreedAt) {
        TermsAgreement termsAgreement = TermsAgreement.builder()
                .isServiceTermsAgreed(isServiceTermsAgreed)
                .isPrivacyPolicyAgreed(isPrivacyPolicyAgreed)
                .isPersonalInformationUsageConsentAgreed(isPersonalInformationUsageConsentAgreed)
                .agreedAt(agreedAt)
                .build();
        if (termsAgreement.allMandatoryTermsAgreed()) {
            return termsAgreement;
        } else {
            throw new IllegalArgumentException("모든 필수 약관이 동의되어야 함");
        }
    }

    private boolean allMandatoryTermsAgreed() {
        return isServiceTermsAgreed && isPrivacyPolicyAgreed && isPersonalInformationUsageConsentAgreed;
    }

    public void renewAgreedAt(LocalDateTime newAgreedAt) {
        Assert.notNull(newAgreedAt, "약관 동의 시점은 null일 수 없음");
        this.agreedAt = newAgreedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TermsAgreement other)) return false;
        return this.isServiceTermsAgreed ==  other.isServiceTermsAgreed
            && this.isPrivacyPolicyAgreed ==  other.isPrivacyPolicyAgreed
            && this.isPersonalInformationUsageConsentAgreed ==  other.isPersonalInformationUsageConsentAgreed;
    }

}
