package soma.ghostrunner.domain.auth.application;

import org.assertj.core.api.Assertions;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.auth.api.dto.TermsAgreementDto;
import soma.ghostrunner.domain.auth.api.dto.request.SignUpRequest;
import soma.ghostrunner.domain.auth.api.dto.response.AuthenticationResponse;
import soma.ghostrunner.domain.auth.application.dto.JwtTokens;
import soma.ghostrunner.domain.auth.resolver.impl.FirebaseUidResolver;
import soma.ghostrunner.domain.member.Gender;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberRepository;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.dao.MemberAuthInfoRepository;
import soma.ghostrunner.domain.member.domain.MemberAuthInfo;
import soma.ghostrunner.global.security.jwt.factory.JwtTokenFactory;

import java.time.LocalDateTime;

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
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 존재하는 사용자");
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

}
