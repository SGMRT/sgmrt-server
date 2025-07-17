package soma.ghostrunner.domain.member.domain;

import jakarta.persistence.*;
import lombok.*;
import soma.ghostrunner.domain.member.Member;

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

    private boolean isServiceTermsAgreed;
    private boolean isPrivacyPolicyAgreed;
    private boolean isDataConsignmentAgreed;
    private boolean isThirdPartyDataSharingAgreed;
    private boolean isMarketingAgreed;

    private LocalDateTime agreedAt;

    @Builder(access = AccessLevel.PRIVATE)
    public TermsAgreement(boolean isServiceTermsAgreed, boolean isPrivacyPolicyAgreed, boolean isDataConsignmentAgreed,
                          boolean isThirdPartyDataSharingAgreed, boolean isMarketingAgreed, LocalDateTime agreedAt) {
        this.isServiceTermsAgreed = isServiceTermsAgreed;
        this.isPrivacyPolicyAgreed = isPrivacyPolicyAgreed;
        this.isDataConsignmentAgreed = isDataConsignmentAgreed;
        this.isThirdPartyDataSharingAgreed = isThirdPartyDataSharingAgreed;
        this.isMarketingAgreed = isMarketingAgreed;
        this.agreedAt = agreedAt;
    }

    public static TermsAgreement of(boolean isServiceTermsAgreed, boolean isPrivacyPolicyAgreed,
                                    boolean isDataConsignmentAgreed, boolean isThirdPartyDataSharingAgreed,
                                    boolean isMarketingAgreed, LocalDateTime agreedAt) {
        return TermsAgreement.builder()
                .isServiceTermsAgreed(isServiceTermsAgreed)
                .isPrivacyPolicyAgreed(isPrivacyPolicyAgreed)
                .isDataConsignmentAgreed(isDataConsignmentAgreed)
                .isThirdPartyDataSharingAgreed(isThirdPartyDataSharingAgreed)
                .isMarketingAgreed(isMarketingAgreed)
                .agreedAt(agreedAt)
                .build();
    }

    public void verifyAllMandatoryTermsAgreed() {
        boolean isAgreed = isServiceTermsAgreed && isPrivacyPolicyAgreed && isDataConsignmentAgreed
                && isThirdPartyDataSharingAgreed;
        if (!isAgreed) {
            throw new IllegalArgumentException("모든 필수 약관이 동의되어야 함");
        }
    }

}
