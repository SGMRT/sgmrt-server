package soma.ghostrunner.domain.auth.application;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.member.api.dto.TermsAgreementDto;
import soma.ghostrunner.domain.auth.api.dto.request.SignUpRequest;
import soma.ghostrunner.domain.auth.api.dto.response.AuthenticationResponse;
import soma.ghostrunner.domain.auth.application.dto.JwtTokens;
import soma.ghostrunner.domain.auth.exception.TokenTheftException;
import soma.ghostrunner.domain.auth.resolver.impl.FirebaseUidResolver;
import soma.ghostrunner.domain.member.exception.InvalidMemberException;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.dao.MemberRepository;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.dao.MemberAuthInfoRepository;
import soma.ghostrunner.domain.member.domain.MemberAuthInfo;
import soma.ghostrunner.domain.member.enums.Gender;
import soma.ghostrunner.global.security.jwt.JwtUserDetails;
import soma.ghostrunner.global.security.jwt.factory.JwtTokenFactory;
import soma.ghostrunner.global.security.jwt.support.JwtProvider;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.BDDMockito.*;

class AuthServiceTest extends IntegrationTestSupport {

    @Autowired
    private AuthService authService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberAuthInfoRepository memberAuthInfoRepository;

    @MockitoBean
    private FirebaseUidResolver firebaseUidResolver;

    @MockitoBean
    private JwtTokenFactory jwtTokenFactory;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private JwtProvider jwtProvider;
  
    @DisplayName("파이어베이스 토큰을 통해 로그인을 진행한다.")
    @Test
    void signIn() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        MemberAuthInfo memberAuthInfo = createMemberAuthInfo(member, "이복둥의 파이어베이스 Uid");
        memberAuthInfoRepository.save(memberAuthInfo);

        given(firebaseUidResolver.resolveAuthId("Firebase Token")).willReturn("이복둥의 파이어베이스 Uid");
        given(jwtTokenFactory.createTokens(member.getUuid()))
                .willReturn(new JwtTokens("access token", "refresh token"));

        // when
        AuthenticationResponse authenticationResponse = authService.signIn("Firebase Token");

        // then
        Assertions.assertThat(authenticationResponse.accessToken()).isEqualTo("access token");
        Assertions.assertThat(authenticationResponse.refreshToken()).isEqualTo("refresh token");
    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    @DisplayName("파이어베이스 토큰을 통해 회원가입을 진행한다.")
    @Test
    void signUp() {
        // given
        TermsAgreementDto agreement = createTermsAgreementDto();
        SignUpRequest request = createSignUpRequest("이복둥", agreement);

        given(firebaseUidResolver.resolveAuthId("Firebase Token")).willReturn("이복둥의 파이어베이스 Uid");
        given(jwtTokenFactory.createTokens(any()))
                .willReturn(new JwtTokens("access token", "refresh token"));

        // when
        AuthenticationResponse authenticationResponse = authService.signUp("Firebase Token", request);

        // then
        Member savedMember = memberRepository.findByExternalAuthUid("이복둥의 파이어베이스 Uid").get();
        Assertions.assertThat(savedMember.getNickname()).isEqualTo("이복둥");

        boolean haveSaved = memberAuthInfoRepository.existsByExternalAuthUid("이복둥의 파이어베이스 Uid");
        Assertions.assertThat(haveSaved).isTrue();

        Assertions.assertThat(authenticationResponse.accessToken()).isEqualTo("access token");
        Assertions.assertThat(authenticationResponse.refreshToken()).isEqualTo("refresh token");
    }

    @DisplayName("이미 가입되어 있는 회원이라면 회원가입에 실패한다.")
    @Test
    void singUpWithAlreadyExistingMember() {
        // given
        Member existMember = createMember("이복둥");
        memberRepository.save(existMember);

        MemberAuthInfo memberAuthInfo = createMemberAuthInfo(existMember, "이복둥의 파이어베이스 Uid");
        memberAuthInfoRepository.save(memberAuthInfo);

        TermsAgreementDto agreement = createTermsAgreementDto();
        SignUpRequest request = createSignUpRequest("다시 가입하려는 이복둥", agreement);

        given(firebaseUidResolver.resolveAuthId("Firebase Token")).willReturn("이복둥의 파이어베이스 Uid");
        given(jwtTokenFactory.createTokens(any()))
                .willReturn(new JwtTokens("access token", "refresh token"));

        // when // then
        Assertions.assertThatThrownBy(() -> authService.signUp("Firebase Token", request))
                .isInstanceOf(InvalidMemberException.class)
                .hasMessage("이미 존재하는 회원인 경우");
    }

    private MemberAuthInfo createMemberAuthInfo(Member existMember, String authUid) {
        return MemberAuthInfo.of(existMember, authUid);
    }

    private SignUpRequest createSignUpRequest(String nickname, TermsAgreementDto agreement) {
        return new SignUpRequest(nickname, "https://example.com/profile.jpg",
                Gender.FEMALE, 165, 55, agreement);
    }

    private TermsAgreementDto createTermsAgreementDto() {
        return new TermsAgreementDto(true, true, true,
                true, false, LocalDateTime.now());
    }

    @DisplayName("필수 약관에 모두 동의하지 않으면 회원가입을 실패한다.")
    @Test
    void singUpWithoutMandatoryTermsAgreements() {
        // given
        TermsAgreementDto agreement = createInvalidTermsAgreementDto();
        SignUpRequest request = createSignUpRequest("이복둥", agreement);

        given(firebaseUidResolver.resolveAuthId("Firebase Token")).willReturn("이복둥의 파이어베이스 Uid");
        given(jwtTokenFactory.createTokens(any()))
                .willReturn(new JwtTokens("access token", "refresh token"));

        // when // then
        Assertions.assertThatThrownBy(() -> authService.signUp("Firebase Token", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("모든 필수 약관이 동의되어야 함");
    }

    private TermsAgreementDto createInvalidTermsAgreementDto() {
        return new TermsAgreementDto(true, false, false,
                true, false, LocalDateTime.now());
    }

    @DisplayName("이미 존재하는 닉네임을 가진 사용자가 있다면 회원가입에 실패한다.")
    @Test
    void singUpWithAlreadyExistNickname() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        TermsAgreementDto agreement = createTermsAgreementDto();
        SignUpRequest request = createSignUpRequest("이복둥", agreement);

        // when // then
        Assertions.assertThatThrownBy(() -> authService.signUp("Firebase Token", request))
                .isInstanceOf(InvalidMemberException.class)
                .hasMessage("이미 존재하는 닉네임인 경우");
    }

    @DisplayName("유효한 리프레쉬 토큰으로 토큰 재발급을 요청하면, 새로운 토큰들이 발급된다.")
    @Test
    void testReissueTokens_Success() {
        // given
        String memberUuid = UUID.randomUUID().toString();
        String validRefreshToken = "valid-refresh-token";
        String newAccessToken = "new-access-token";
        String newRefreshToken = "new-refresh-token-rotated";

        // Mock 객체들의 동작 정의
        given(jwtProvider.getUserIdFromToken(validRefreshToken)).willReturn(memberUuid);
        given(refreshTokenService.findTokenByMemberUuid(memberUuid)).willReturn(Optional.of(validRefreshToken));
        given(jwtTokenFactory.createTokens(memberUuid)).willReturn(new JwtTokens(newAccessToken, newRefreshToken));

        // when
        AuthenticationResponse response = authService.reissueTokens(validRefreshToken);

        // then
        Assertions.assertThat(response.uuid()).isEqualTo(memberUuid);
        Assertions.assertThat(response.accessToken()).isEqualTo(newAccessToken);
        Assertions.assertThat(response.refreshToken()).isEqualTo(newRefreshToken);
    }

    @DisplayName("요청받은 리프레쉬 토큰이 저장소의 토큰과 다르면, 탈취로 간주하고 예외를 발생시킨다.")
    @Test
    void testReissueTokens_TheftDetected_Mismatch() {
        // given
        String memberUuid = UUID.randomUUID().toString();
        String requestRefreshToken = "request-token-possibly-stolen";
        String storedRefreshToken = "different-token-in-storage";

        given(jwtProvider.getUserIdFromToken(requestRefreshToken)).willReturn(memberUuid);
        given(refreshTokenService.findTokenByMemberUuid(memberUuid)).willReturn(Optional.of(storedRefreshToken));

        // when & then
        Assertions.assertThatThrownBy(() -> authService.reissueTokens(requestRefreshToken))
                .isInstanceOf(TokenTheftException.class)
                .hasMessage("요청된 리프레쉬 토큰이 저장소의 토큰과 일치하지 않아 공격이 의심되는 상황");

        then(refreshTokenService).should(times(1)).deleteTokenByMemberUuid(memberUuid);
    }

    @DisplayName("저장소에 리프레쉬 토큰이 존재하지 않으면, 탈취로 간주하고 예외를 발생시킨다.")
    @Test
    void testReissueTokens_TheftDetected_NotFound() {
        // given
        String memberUuid = UUID.randomUUID().toString();
        String requestRefreshToken = "request-token";

        given(jwtProvider.getUserIdFromToken(requestRefreshToken)).willReturn(memberUuid);
        given(refreshTokenService.findTokenByMemberUuid(memberUuid)).willReturn(Optional.empty());

        // when & then
        Assertions.assertThatThrownBy(() -> authService.reissueTokens(requestRefreshToken))
                .isInstanceOf(TokenTheftException.class)
                .hasMessage("저장소에 멤버의 리프레쉬 토큰이 없지만 재발급 요청되어 공격이 의심되는 상황");
    }

    @DisplayName("인증된 회원이 본인의 uuid로 isOwner()를 호출하는 경우 true를 반환한다.")
    @Test
    void testIsOwner_success() {
        // given
        String memberUuid = UUID.randomUUID().toString();
        JwtUserDetails userDetails = new JwtUserDetails(memberUuid);

        // when
        boolean result = authService.isOwner(memberUuid, userDetails);

        // then
        Assertions.assertThat(result).isTrue();
    }

    @DisplayName("인증된 회원이 다른 uuid로 isOwner()를 호출하는 경우 false를 반환한다.")
    @Test
    void testIsOwner_notMatch() {
        // given
        String memberUuid = "wrong-uuid";
        JwtUserDetails userDetails = new JwtUserDetails(UUID.randomUUID().toString());

        // when
        boolean result = authService.isOwner(memberUuid, userDetails);

        // then
        Assertions.assertThat(result).isFalse();
    }

    @DisplayName("인증되지 않은 회원이 isOwner()를 호출하면 false를 반환한다.")
    @Test
    void testIsOwner_notAuthenticated() {
        // given
        String memberUuid = UUID.randomUUID().toString();
        JwtUserDetails userDetails = null;

        // when
        boolean result = authService.isOwner(memberUuid, userDetails);

        // then
        Assertions.assertThat(result).isFalse();
    }

}
