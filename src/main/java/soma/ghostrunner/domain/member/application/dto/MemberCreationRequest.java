package soma.ghostrunner.domain.member.application.dto;

import lombok.*;
import soma.ghostrunner.domain.member.enums.Gender;
import soma.ghostrunner.domain.member.domain.TermsAgreement;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MemberCreationRequest {
    private String externalAuthId;
    private String profileImageUrl;
    private String nickname;
    private Gender gender;
    private Integer height;
    private Integer weight;
    private TermsAgreement termsAgreement;

}
