package soma.ghostrunner.domain.member.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import soma.ghostrunner.ApiTestSupport;
import soma.ghostrunner.domain.member.RunningLevel;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberApiTest extends ApiTestSupport {

    @DisplayName("멤버의 VDOT를 조회한다.")
    @Test
    void getVdot() throws Exception {
        // when // then
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/members/vdot")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                )
                .andDo(print())
                .andExpect(status().isOk())
        ;
    }

    @DisplayName("멤버의 VDOT를 계산하고 저장한다.")
    @Test
    void postVdot() throws Exception {
        // when // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/members/vdot")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .queryParam("level", RunningLevel.BEGINNER.name())
                )
                .andDo(print())
                .andExpect(status().isOk())
        ;
    }

    @DisplayName("멤버의 VDOT를 계산하고 저장할 때 러닝 레벨은 유효해야한다.")
    @Test
    void postVdotAndMustBeValid() throws Exception {
        // when // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/members/vdot")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("level", "FAKE LEVEL")
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
        ;
    }

    @DisplayName("멤버의 VDOT를 계산하고 저장할 때 러닝 레벨은 필수 값이다.")
    @Test
    void postVdotAndMustNotBeNull() throws Exception {
        // when // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/members/vdot")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
        ;
    }

}
