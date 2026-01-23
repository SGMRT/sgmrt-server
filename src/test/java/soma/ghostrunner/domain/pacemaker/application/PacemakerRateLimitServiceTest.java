package soma.ghostrunner.domain.pacemaker.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.domain.running.infra.redis.RedisRunningRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacemakerRateLimitServiceTest {

    @Mock
    RedisRunningRepository redisRunningRepository;

    PacemakerRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new PacemakerRateLimitService(redisRunningRepository);
    }

    @Test
    @DisplayName("남은 일일 사용량을 조회한다 - 1회 사용한 경우")
    void getRemainingCount_withOneUsage() {
        // given
        String memberUuid = "member-123";
        when(redisRunningRepository.get(anyString())).thenReturn("1");

        // when
        Long remaining = rateLimitService.getRemainingCount(memberUuid);

        // then
        assertThat(remaining).isEqualTo(PacemakerRateLimitService.DAILY_LIMIT - 1);
    }

    @Test
    @DisplayName("남은 일일 사용량을 조회한다 - 초과한 경우 0을 반환")
    void getRemainingCount_exceeded() {
        // given
        String memberUuid = "member-123";
        when(redisRunningRepository.get(anyString())).thenReturn("5");

        // when
        Long remaining = rateLimitService.getRemainingCount(memberUuid);

        // then
        assertThat(remaining).isEqualTo(0);
    }

    @Test
    @DisplayName("남은 일일 사용량을 조회한다 - 사용 기록이 없으면 DAILY_LIMIT 반환")
    void getRemainingCount_noUsage() {
        // given
        String memberUuid = "member-123";
        when(redisRunningRepository.get(anyString())).thenReturn(null);

        // when
        Long remaining = rateLimitService.getRemainingCount(memberUuid);

        // then
        assertThat(remaining).isEqualTo(PacemakerRateLimitService.DAILY_LIMIT);
    }

    @Test
    @DisplayName("Rate Limit 카운터를 증가시킨다")
    void incrementCounter_success() {
        // given
        String memberUuid = "member-123";
        when(redisRunningRepository.incrementRateLimitCounter(anyString(), anyLong(), anyInt()))
                .thenReturn(1L);

        // when
        rateLimitService.incrementCounter(memberUuid);

        // then
        verify(redisRunningRepository).incrementRateLimitCounter(
                contains("pacemaker_api_rate_limit:" + memberUuid),
                eq(PacemakerRateLimitService.DAILY_LIMIT),
                eq(86400)
        );
    }

    @Test
    @DisplayName("일일 사용량 초과 시 InvalidRunningException을 던진다")
    void incrementCounter_exceeded_throwsException() {
        // given
        String memberUuid = "member-123";
        when(redisRunningRepository.incrementRateLimitCounter(anyString(), anyLong(), anyInt()))
                .thenReturn(-1L);

        // when/then
        assertThatThrownBy(() -> rateLimitService.incrementCounter(memberUuid))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessageContaining("일일 사용량");
    }

    @Test
    @DisplayName("Redis 스크립트 오류 시 RuntimeException을 던진다")
    void incrementCounter_redisError_throwsException() {
        // given
        String memberUuid = "member-123";
        when(redisRunningRepository.incrementRateLimitCounter(anyString(), anyLong(), anyInt()))
                .thenReturn(null);

        // when/then
        assertThatThrownBy(() -> rateLimitService.incrementCounter(memberUuid))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Redis 스크립트");
    }

    @Test
    @DisplayName("Rate Limit 카운터를 감소시킨다 (보상)")
    void decrementCounter_success() {
        // given
        String rateLimitKey = "pacemaker_api_rate_limit:member-123:2024-01-01";

        // when
        rateLimitService.decrementCounter(rateLimitKey);

        // then
        verify(redisRunningRepository).decrementRateLimitCounter(rateLimitKey);
    }

    @Test
    @DisplayName("Rate Limit 키를 생성한다")
    void createRateLimitKey_format() {
        // given
        String memberUuid = "member-123";

        // when
        String key = rateLimitService.createRateLimitKey(memberUuid);

        // then
        assertThat(key).startsWith("pacemaker_api_rate_limit:" + memberUuid + ":");
        assertThat(key).matches("pacemaker_api_rate_limit:member-123:\\d{4}-\\d{2}-\\d{2}");
    }

}
