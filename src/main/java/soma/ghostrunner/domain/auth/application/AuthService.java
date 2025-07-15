package soma.ghostrunner.domain.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import soma.ghostrunner.domain.auth.api.dto.AuthMapper;
import soma.ghostrunner.domain.auth.api.dto.response.SignInResponse;
import soma.ghostrunner.domain.auth.api.dto.response.SignUpResponse;
import soma.ghostrunner.domain.auth.resolver.AuthIdResolver;
import soma.ghostrunner.domain.auth.api.dto.request.SignUpRequest;
import soma.ghostrunner.domain.auth.api.dto.TermsAgreementDto;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberNotFoundException;
import soma.ghostrunner.domain.member.application.dto.MemberCreationRequest;
import soma.ghostrunner.domain.member.domain.TermsAgreement;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberService memberService;
    private final AuthIdResolver authIdResolver;
    private final AuthMapper authMapper;

    public SignInResponse signIn(String authorizationHeader) {
        String externalAuthId = resolveAuthIdOrThrow(authorizationHeader);
        try {
            Member member = memberService.findMemberByAuthUid(externalAuthId);
            // todo access token & refresh token 반환

            return authMapper.toSignInResponse(member.getUuid(), "accessToken", "refreshToken");
        } catch (MemberNotFoundException e) {
            throw new AccessDeniedException("존재하지 않는 사용자");
        }
    }

    @Transactional
    public SignUpResponse signUp(String authorizationHeader, SignUpRequest signUpRequest) {
        String externalAuthId = resolveAuthIdOrThrow(authorizationHeader);
        if(memberService.isMemberExistsByAuthUid(externalAuthId))
            throw new AccessDeniedException("이미 존재하는 사용자");

        TermsAgreement termsAgreement = createTermsAgreement(signUpRequest.getAgreement());
        if(!termsAgreement.areAllMandatoryTermsAgreed())
            throw new IllegalArgumentException("모든 필수 약관이 동의되어야 함");

        String memberUuid = UUID.randomUUID().toString();
        MemberCreationRequest creationRequest = createMemberCreationRequest(
                memberUuid, externalAuthId, signUpRequest, termsAgreement);
        Member newMember = memberService.createMember(creationRequest);

        // todo 토큰 발급 후 dto에 member id, access token, refresh token 받아 반환
        return authMapper.toSignUpResponse(newMember.getUuid(), "accessToken", "refreshToken");
    }

    private TermsAgreement createTermsAgreement(TermsAgreementDto agreementDto) {
        return TermsAgreement.builder()
                .isServiceTermsAgreed(agreementDto.isServiceTermsAgreed())
                .isPrivacyPolicyAgreed(agreementDto.isPrivacyPolicyAgreed())
                .isDataConsignmentAgreed(agreementDto.isDataConsignmentAgreed())
                .isThirdPartyDataSharingAgreed(agreementDto.isThirdPartyDataSharingAgreed())
                .isMarketingAgreed(agreementDto.isMarketingAgreed())
                .agreedAt(LocalDateTime.now())
                .build();
    }

    private MemberCreationRequest createMemberCreationRequest(
            String uuid,
            String externalAuthId,
            SignUpRequest signUpRequest,
            TermsAgreement termsAgreement) {
        return MemberCreationRequest.builder()
                .uuid(uuid)
                .externalAuthId(externalAuthId)
                .profileImageUrl(signUpRequest.getProfileImageUrl())
                .nickname(signUpRequest.getNickname())
                .gender(signUpRequest.getGender())
                .height(signUpRequest.getHeight())
                .weight(signUpRequest.getWeight())
                .termsAgreement(termsAgreement)
                .build();
    }

    private String resolveAuthIdOrThrow(String authorizationHeader) {
        String authToken = extractAuthTokenOrThrow(authorizationHeader);
        return authIdResolver.resolveAuthId(authToken);
    }

    private String extractAuthTokenOrThrow(String authorizationHeader) {
        if(!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer "))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Empty authorization header");

        String token = authorizationHeader.substring("Bearer ".length());
        if(!StringUtils.hasText(token))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Empty token");

        return token;
    }

}
