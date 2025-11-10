package soma.ghostrunner.domain.notification.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.notification.domain.PushHistory;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PushHistoryRepository 통합 테스트")
class PushHistoryRepositoryTest extends IntegrationTestSupport {

    @Autowired PushHistoryRepository pushHistoryRepository;

    @DisplayName("UUID로 PushHistory를 조회한다.")
    @Test
    void findByUuid() {
        // given
        PushHistory pushHistory = PushHistory.of(1L,"제목", "본문", Map.of());
        pushHistoryRepository.save(pushHistory);

        // when
        PushHistory foundHistory = pushHistoryRepository.findByUuid(pushHistory.getUuid()).orElseThrow();

        // then
        assertThat(foundHistory).isNotNull();
        assertThat(foundHistory.getUuid()).isEqualTo(pushHistory.getUuid());
        assertThat(foundHistory.getTitle()).isEqualTo(pushHistory.getTitle());
        assertThat(foundHistory.getBody()).isEqualTo(pushHistory.getBody());
        assertThat(foundHistory.getMemberId()).isEqualTo(pushHistory.getMemberId());
    }

}