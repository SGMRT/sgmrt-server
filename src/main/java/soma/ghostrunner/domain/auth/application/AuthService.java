package soma.ghostrunner.domain.auth.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.auth.api.dto.AuthMapper;
import soma.ghostrunner.domain.auth.api.dto.response.AuthenticationResponse;
import soma.ghostrunner.domain.auth.application.dto.JwtTokens;
import soma.ghostrunner.domain.auth.exception.TokenTheftException;
import soma.ghostrunner.domain.auth.resolver.AuthIdResolver;
import soma.ghostrunner.domain.auth.api.dto.request.SignUpRequest;
import soma.ghostrunner.domain.member.api.dto.TermsAgreementDto;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.application.dto.MemberMapper;
import soma.ghostrunner.domain.member.domain.TermsAgreement;
import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.security.jwt.factory.JwtTokenFactory;
import soma.ghostrunner.global.security.jwt.support.JwtProvider;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final RefreshTokenService refreshTokenService;

    private final MemberService memberService;
    private final MemberMapper memberMapper;

    private final AuthIdResolver authIdResolver;
    private final AuthMapper authMapper;

    private final JwtTokenFactory jwtTokenFactory;
    private final JwtProvider jwtProvider;

    @Transactional
    public AuthenticationResponse signIn(String firebaseToken) {
        String externalAuthId = authIdResolver.resolveAuthId(firebaseToken);
        String memberUuid = findMemberUuid(externalAuthId);
        JwtTokens jwtTokens = createAndSaveTokens(memberUuid);
        return authMapper.toAuthenticationResponse(memberUuid, jwtTokens);
    }

    private String findMemberUuid(String externalAuthId) {
        return memberService.findUuidByAuthUid(externalAuthId);
    }

    private JwtTokens createAndSaveTokens(String memberUuid) {
        JwtTokens jwtTokens = jwtTokenFactory.createTokens(memberUuid);
        refreshTokenService.saveToken(memberUuid, jwtTokens.refreshToken());
        return jwtTokens;
    }

    @Transactional
    public AuthenticationResponse signUp(String firebaseToken, SignUpRequest signUpRequest) {
        String externalAuthId = authIdResolver.resolveAuthId(firebaseToken);
        memberService.verifyMemberExistsByAuthUid(externalAuthId);

        TermsAgreement termsAgreement = createTermsAgreement(signUpRequest.getAgreement());
        Member member = createMember(signUpRequest, externalAuthId, termsAgreement);

        String memberUuid = member.getUuid();
        JwtTokens jwtTokens = createAndSaveTokens(memberUuid);
        return authMapper.toAuthenticationResponse(memberUuid, jwtTokens);
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

    @Transactional
    public AuthenticationResponse reissueTokens(String receivedRefreshToken) {
        String memberUuid = getMemberUuidFromToken(receivedRefreshToken);

        String storedRefreshToken = findRefreshToken(memberUuid);
        verifyRefreshTokenTheft(receivedRefreshToken, storedRefreshToken, memberUuid);

        JwtTokens jwtTokens = createAndSaveTokens(memberUuid);
        refreshTokenService.saveToken(memberUuid, jwtTokens.refreshToken());
        return authMapper.toAuthenticationResponse(memberUuid, jwtTokens);
    }

    private String getMemberUuidFromToken(String receivedRefreshToken) {
        return jwtProvider.getUserIdFromToken(receivedRefreshToken);
    }

    private String findRefreshToken(String memberUuid) {
        return refreshTokenService.findTokenByMemberUuid(memberUuid)
                .orElseThrow(() -> new TokenTheftException(
                        ErrorCode.EXPIRED_BY_THEFT, "저장소에 멤버의 리프레쉬 토큰이 없지만 재발급 요청되어 공격이 의심되는 상황"));
    }

    private void verifyRefreshTokenTheft(String receivedRefreshToken, String storedRefreshToken, String memberUuid) {
        if (!storedRefreshToken.equals(receivedRefreshToken)) {
            refreshTokenService.deleteTokenByMemberUuid(memberUuid);
            throw new TokenTheftException(
                    ErrorCode.EXPIRED_BY_THEFT, "요청된 리프레쉬 토큰이 저장소의 토큰과 일치하지 않아 공격이 의심되는 상황");
        }
    }

    @Transactional
    public void logout(String receivedRefreshToken) {
        String memberUuid = getMemberUuidFromToken(receivedRefreshToken);
        refreshTokenService.deleteTokenByMemberUuid(memberUuid);
    }

}
