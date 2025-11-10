package soma.ghostrunner.domain.notification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PushHistoryTest {

    @DisplayName("PushHistory.of로 객체를 생성하면 필드가 정상 설정된다")
    @Test
    void of() {
        // given
        Long memberId = 123L;
        String title = "제목";
        String body = "본문";
        Map<String, Object> data = new HashMap<>();
        data.put("k", "v");

        // when
        PushHistory history = PushHistory.of(memberId, title, body, data);

        // then
        assertThat(history).isNotNull();
        assertThat(history.getUuid()).isNotBlank();
        assertThat(history.getMemberId()).isEqualTo(memberId);
        assertThat(history.getStatus()).isEqualTo(NotificationStatus.CREATED);
        assertThat(history.getTitle()).isEqualTo(title);
        assertThat(history.getBody()).isEqualTo(body);
        assertThat(history.getData()).containsEntry("k", "v");
        assertThat(history.getReadAt()).isNull();
    }

    @DisplayName("markAsRead은 처음 호출 시 readAt을 설정하고, 이후 호출엔 영향을 주지 않는다")
    @Test
    void markAsRead_idempotent() {
        // given
        PushHistory history = PushHistory.of(1L, "t", "b", new HashMap<>());

        // when
        LocalDateTime first = LocalDateTime.of(2025, 1, 1, 10, 0);
        history.markAsRead(first);

        // then
        assertThat(history.getReadAt()).isEqualTo(first);
        assertThat(history.getStatus()).isEqualTo(NotificationStatus.DELIVERED);

        // when - 두번째 호출은 기존 값 유지
        LocalDateTime second = LocalDateTime.of(2025, 2, 2, 12, 0);
        history.markAsRead(second);

        // then
        assertThat(history.getReadAt()).isEqualTo(first);
        assertThat(history.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
    }

    @DisplayName("uuid는 of 생성 시 자동 생성되며 형식이 유효하다")
    @Test
    void uuid_is_generated_and_valid_format() {
        // when
        PushHistory history = PushHistory.of(1L, "t", "b", new HashMap<>());

        // then
        String uuid = history.getUuid();
        assertThat(uuid).isNotBlank();
        assertThat(uuid.length()).isEqualTo(36); // 8-4-4-4-12
        UUID parsed = UUID.fromString(uuid);
        assertThat(parsed).isNotNull();
    }

}
