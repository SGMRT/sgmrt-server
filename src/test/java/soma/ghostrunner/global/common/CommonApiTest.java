package soma.ghostrunner.global.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import soma.ghostrunner.ApiTestSupport;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommonApiTest extends ApiTestSupport {

    @DisplayName("회원가입 시 프로필 사진을 등록하기 위해 PresignUrl을 요청한다.")
    @Test
    void generateMemberPresignUrl() throws Exception {
        // when // then
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/common/presign-url")
                        .queryParam("type", "MEMBER_PROFILE")
                        .queryParam("fileName", "이복둥.jpg")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk());
    }

    @DisplayName("회원가입 시 프로필 사진을 등록하기 위해 PresignUrl을 요청할 때 유효하지 않은 타입이면 예외를 응답한다.")
    @Test
    void generateMemberPresignUrlWithInvalidRequestType() throws Exception {
        // when // then
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/common/presign-url")
                        .queryParam("type", "INVALID_TYPE")
                        .queryParam("fileName", "이복둥.jpg")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("G-001"))
                .andExpect(jsonPath("$.message").value("잘못된 요청 데이터"));;
    }

}
