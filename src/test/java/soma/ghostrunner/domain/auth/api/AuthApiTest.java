package soma.ghostrunner.domain.auth.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import soma.ghostrunner.ApiTestSupport;
import soma.ghostrunner.domain.member.api.dto.TermsAgreementDto;
import soma.ghostrunner.domain.auth.api.dto.request.SignUpRequest;
import soma.ghostrunner.domain.member.enums.Gender;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends ApiTestSupport {

    @DisplayName("Authorization 헤더에서 파이어베이스 토큰을 추출하고 로그인을 진행한다.")
    @Test
    void firebaseSignIn() throws Exception {
        // when // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/firebase-signin")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Authorization", "Bearer Firebase's Uid")
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk());
    }

    @DisplayName("Authorization 헤더가 비어있다면 예외가 발생한다.")
    @Test
    void firebaseSignInWithNoneAuthorizationHeader() throws Exception {
        // when // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/firebase-signin")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("G-009"))
                .andExpect(jsonPath("$.message").value("잘못되거나 비어있는 헤더"));
    }

    @DisplayName("Authorization 헤더가 유효하지 않다면 예외가 발생한다.")
    @Test
    void firebaseSignInWithInvalidAuthorizationHeader() throws Exception {
        // when // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/firebase-signin")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Authorization", "invalid")
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A-004"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 토큰"));
    }

    @DisplayName("Authorization 헤더에서 파이어베이스 토큰을 추출하고 회원가입을 진행한다.")
    @Test
    void firebaseSignUp() throws Exception {
        // given
        SignUpRequest request = createSignUpRequest("이복둥", createTermsAgreementDto());

        // when // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/firebase-signup")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Authorization", "Bearer Firebase's Uid")
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk());
    }

    private SignUpRequest createSignUpRequest(String nickname, TermsAgreementDto agreement) {
        return new SignUpRequest(nickname, "https://example.com/profile.jpg",
                Gender.FEMALE, 165, 55, agreement);
    }

    private TermsAgreementDto createTermsAgreementDto() {
        return new TermsAgreementDto(true, true, true,
                true, false, LocalDateTime.now());
    }

    @DisplayName("Authorization 헤더에서 리프레쉬 토큰을 추출하고 토큰 재발급을 진행한다.")
    @Test
    void reissue() throws Exception {
        // when // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/reissue")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Authorization", "Bearer RefreshToken")
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk());
    }

    @DisplayName("Authorization 헤더에서 리프레쉬 토큰을 추출하고 로그아웃을 진행한다.")
    @Test
    void logout() throws Exception {
        // when // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/logout")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Authorization", "Bearer RefreshToken")
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk());
    }
  
}
