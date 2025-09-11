package soma.ghostrunner.domain.notification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.member.domain.Member;

import static org.assertj.core.api.Assertions.*;

class PushTokenTest {

    @DisplayName("PushToken을 성공적으로 생성할 수 있다.")
    @Test
    void createPushToken() {
        // given
        Member member = Member.of("짱구", "profile-url");
        String token = "test-token";

        // when
        PushToken pushToken = new PushToken(member, token);

        // then
        assertThat(pushToken.getToken()).isEqualTo(token);
        assertThat(member.getNickname()).isEqualTo(pushToken.getMember().getNickname());
    }
}