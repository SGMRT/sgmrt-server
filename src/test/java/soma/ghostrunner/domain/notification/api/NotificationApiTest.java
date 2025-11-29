package soma.ghostrunner.domain.notification.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import soma.ghostrunner.ApiTestSupport;
import soma.ghostrunner.global.security.jwt.JwtUserDetails;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationApiTest extends ApiTestSupport {

    @DisplayName("푸시 알림을 읽음 처리한다")
    @Test
    void markPushNotificationAsRead_success() throws Exception {
        // given
        var pushId = "uuid1234";

        JwtUserDetails userDetails = new JwtUserDetails("userId");
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/push/{messageUuid}", pushId)
                    .with(authentication(authentication))
                    .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andDo(print())
                .andExpect(status().isOk());

        verify(pushService).markAsRead(pushId);
    }

}

