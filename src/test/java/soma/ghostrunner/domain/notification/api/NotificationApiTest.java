package soma.ghostrunner.domain.notification.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import soma.ghostrunner.ApiTestSupport;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationApiTest extends ApiTestSupport {

    @DisplayName("푸시 알림을 읽음 처리한다")
    @Test
    void markPushNotificationAsRead_success() throws Exception {
        // given
        var pushId = "uuid";

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/push/{pushUuid}", pushId))
                .andDo(print())
                .andExpect(status().isOk());

        verify(pushService).markAsRead(pushId);
    }

}

