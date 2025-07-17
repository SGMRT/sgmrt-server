package soma.ghostrunner.domain.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.auth.api.dto.AuthMapper;
import soma.ghostrunner.domain.auth.api.dto.response.AuthenticationResponse;
import soma.ghostrunner.domain.auth.application.dto.JwtTokens;
import soma.ghostrunner.domain.auth.resolver.AuthIdResolver;
import soma.ghostrunner.domain.auth.api.dto.request.SignUpRequest;
import soma.ghostrunner.domain.auth.api.dto.TermsAgreementDto;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.application.dto.MemberMapper;
import soma.ghostrunner.domain.member.domain.TermsAgreement;
import soma.ghostrunner.global.security.jwt.factory.JwtTokenFactory;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberService memberService;
    private final MemberMapper memberMapper;

    private final AuthIdResolver authIdResolver;
    private final AuthMapper authMapper;

    private final JwtTokenFactory jwtTokenFactory;

    // TODO : 리프레쉬 토큰을 레디스에 저장한다.
    public AuthenticationResponse signIn(String firebaseToken) {
        String externalAuthId = authIdResolver.resolveAuthId(firebaseToken);
        String memberUuid = findMemberUuid(externalAuthId);
        return authMapper.toAuthenticationResponse(memberUuid, createTokens(memberUuid));
    }

    private String findMemberUuid(String externalAuthId) {
        return memberService.findUuidByAuthUid(externalAuthId);
    }

    private JwtTokens createTokens(String memberUuid) {
        return jwtTokenFactory.createTokens(memberUuid);
    }

    @Transactional
    public AuthenticationResponse signUp(String firebaseToken, SignUpRequest signUpRequest) {
        String externalAuthId = authIdResolver.resolveAuthId(firebaseToken);
        memberService.verifyMemberExistsByAuthUid(externalAuthId);

        TermsAgreement termsAgreement = createTermsAgreement(signUpRequest.getAgreement());
        Member member = createMember(signUpRequest, externalAuthId, termsAgreement);

        String memberUuid = member.getUuid();
        return authMapper.toAuthenticationResponse(memberUuid, createTokens(memberUuid));
    }

    private TermsAgreement createTermsAgreement(TermsAgreementDto agreementDto) {
        return TermsAgreement.createIfAllMandatoryTermsAgreed(agreementDto.isServiceTermsAgreed(), agreementDto.isPrivacyPolicyAgreed(),
                agreementDto.isDataConsignmentAgreed(), agreementDto.isThirdPartyDataSharingAgreed(),
                agreementDto.isMarketingAgreed(), LocalDateTime.now());
    }

    private Member createMember(SignUpRequest signUpRequest, String externalAuthId, TermsAgreement termsAgreement) {
        return memberService.createMember(
                memberMapper.toMemberCreationRequest(externalAuthId, signUpRequest, termsAgreement));
    }

}
