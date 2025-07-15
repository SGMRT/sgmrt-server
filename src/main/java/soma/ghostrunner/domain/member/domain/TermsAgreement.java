package soma.ghostrunner.domain.member.domain;

import jakarta.persistence.*;
import lombok.*;
import soma.ghostrunner.domain.member.Member;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
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

    public boolean areAllMandatoryTermsAgreed() {
        return isServiceTermsAgreed && isPrivacyPolicyAgreed && isDataConsignmentAgreed && isThirdPartyDataSharingAgreed;
    }
}
