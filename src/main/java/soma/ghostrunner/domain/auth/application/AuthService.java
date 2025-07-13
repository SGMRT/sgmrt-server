package soma.ghostrunner.domain.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import soma.ghostrunner.domain.auth.AuthIdResolver;
import soma.ghostrunner.domain.auth.SignUpRequest;
import soma.ghostrunner.domain.member.MemberService;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberNotFoundException;
import soma.ghostrunner.domain.member.domain.TermsAgreement;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberService memberService;
    private final AuthIdResolver authIdResolver;

    public Object signIn(String authorizationHeader) {
        String externalAuthId = resolveAuthIdOrThrow(authorizationHeader);
        try {
            Member member = memberService.findMemberByAuthUid(externalAuthId);
            // todo access token & refresh token 반환

            return null;
        } catch (MemberNotFoundException e) {
            throw new AuthenticationServiceException("존재하지 않는 사용자");
        }
    }

    // todo
    // 헤더에 firebase id token을 입력으로 받음
    // 추가적으로 약관 동의 여부, 닉네임, 프로필 사진 url (presigned url), 성별, 그리고 nullable한 키 몸무게를 입력으로 받음
    // - firebase uuid가 존재하는 경우 예외 반환
    // - 회원 생성 후 필요하다고 판단되는 데이터 반환
    @Transactional
    public Object signUp(String authorizationHeader, SignUpRequest signUpRequest) {
        String externalAuthId = resolveAuthIdOrThrow(authorizationHeader);
        if(memberService.isMemberExistsByAuthUid(externalAuthId))
            throw new AccessDeniedException("이미 존재하는 사용자");

        TermsAgreement termsAgreement = TermsAgreement.builder()
                .isServiceTermsAgreed(signUpRequest.getAgreement().isServiceTermsAgreed())
                .isDataConsignmentAgreed(signUpRequest.getAgreement().isDataConsignmentAgreed())
                .isPrivacyPolicyAgreed(signUpRequest.getAgreement().isPrivacyPolicyAgreed())
                .isThirdPartyDataSharingAgreed(signUpRequest.getAgreement().isThirdPartyDataSharingAgreed())
                .isMarketingAgreed(signUpRequest.getAgreement().isMarketingAgreed())
                .agreedAt(LocalDateTime.now())
                .build();
        if(!termsAgreement.areAllMandatoryTermsAgreed())
            throw new IllegalArgumentException("모든 필수 약관이 동의되어야 함");


        Member newMember = memberService.createMember(externalAuthId, signUpRequest);

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
