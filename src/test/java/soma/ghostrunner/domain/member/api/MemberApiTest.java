package soma.ghostrunner.domain.member.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import soma.ghostrunner.ApiTestSupport;

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

}
