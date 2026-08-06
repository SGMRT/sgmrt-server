package soma.ghostrunner.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 이 커스터마이저가 값을 실제로 쓰는 것이 셀 캐시의 "요청 단위 직행 강등"(설계 결정 2)의 유일한 근거다.
 * Redisson 기본값(응답 3s · 재시도 3회 × 1.5s)이면 호출자는 ~7.5초를 readOnly 트랜잭션 안에서 기다린다.
 *
 * <p>값 자체(500/500/1/200)는 장애 중에 조정하는 <b>운영 레버</b>라 여기서 고정하지 않는다(설계 §8-2).
 * 고정하는 것은 <b>"주어진 값을 지원 토폴로지 전부에 빠짐없이 쓴다"</b>는 메커니즘이다 —
 * 토폴로지가 바뀌었을 때 커스터마이저가 조용히 아무것도 하지 않는 회귀를 잡는다.
 * 지원 토폴로지 3갈래(single·cluster·sentinel)를 모두 세우고, 그 밖의 구성에서는
 * <b>값 미적용은 감수하되 예외를 던지지 않는다</b>는 fail-safe까지 함께 고정한다 —
 * 여기서 던지면 값이 안 먹는 게 아니라 애플리케이션이 아예 뜨지 않는다.</p>
 *
 * <p>커스터마이저는 순수 람다라 스프링 컨텍스트 없이 직접 호출해 검증한다.</p>
 */
class RedisConfigTest {

    private static final Duration COMMAND_TIMEOUT = Duration.ofMillis(500);
    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(500);
    private static final int RETRY_ATTEMPTS = 1;
    private static final Duration RETRY_INTERVAL = Duration.ofMillis(200);

    @Test
    @DisplayName("타임아웃 커스터마이저는 단일 서버 구성에 응답·연결·재시도 값을 모두 적용한다")
    void customizer_AppliesAllValues_ToSingleServer() {
        // given : 자동설정이 만드는 것과 같은 단일 서버 Config
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");

        // when
        customize(config);

        // then : 호출자 대기 상한 = (timeout + retryInterval) × (retryAttempts + 1)
        SingleServerConfig applied = config.useSingleServer();
        assertThat(applied.getTimeout()).isEqualTo(500);
        assertThat(applied.getConnectTimeout()).isEqualTo(500);
        assertThat(applied.getRetryAttempts()).isEqualTo(1);
        assertThat(applied.getRetryInterval()).isEqualTo(200);
    }

    @Test
    @DisplayName("타임아웃 커스터마이저는 클러스터 구성에도 같은 값을 적용한다 — 토폴로지가 바뀌어도 강등 보증은 유지된다")
    void customizer_AppliesAllValues_ToClusterServers() {
        // given
        Config config = new Config();
        config.useClusterServers().addNodeAddress("redis://127.0.0.1:6379");

        // when
        customize(config);

        // then : single 전용 가드로 되돌리면 여기서 Redisson 기본값(3s / 3회 × 1.5s)이 남아 빨개진다
        ClusterServersConfig applied = config.useClusterServers();
        assertThat(applied.getTimeout()).isEqualTo(500);
        assertThat(applied.getConnectTimeout()).isEqualTo(500);
        assertThat(applied.getRetryAttempts()).isEqualTo(1);
        assertThat(applied.getRetryInterval()).isEqualTo(200);
    }

    @Test
    @DisplayName("타임아웃 커스터마이저는 센티널 구성에도 같은 값을 적용한다")
    void customizer_AppliesAllValues_ToSentinelServers() {
        // given
        Config config = new Config();
        config.useSentinelServers().setMasterName("mymaster").addSentinelAddress("redis://127.0.0.1:26379");

        // when
        customize(config);

        // then : 세 번째 분기를 복붙 오타(use*Servers 잘못 지정)로 되돌리면 여기서만 빨개진다
        SentinelServersConfig applied = config.useSentinelServers();
        assertThat(applied.getTimeout()).isEqualTo(500);
        assertThat(applied.getConnectTimeout()).isEqualTo(500);
        assertThat(applied.getRetryAttempts()).isEqualTo(1);
        assertThat(applied.getRetryInterval()).isEqualTo(200);
    }

    @Test
    @DisplayName("미지원 토폴로지에서는 예외를 던지지 않는다 — 값 미적용은 감수하되 기동은 깨뜨리지 않는다")
    void customizer_DoesNotThrow_OnUnsupportedTopology() {
        // given : masterSlave는 Config에 판별자가 없어 감지할 수 없는 구성이다
        Config config = new Config();
        config.useMasterSlaveServers().setMasterAddress("redis://127.0.0.1:6379");

        // when & then : 여기서 던지면 RedissonAutoConfiguration이 Config를 만드는 도중 터져 앱이 아예 뜨지 않는다
        assertThatCode(() -> customize(config)).doesNotThrowAnyException();
    }

    private void customize(Config config) {
        new RedisConfig()
                .redisTimeoutCustomizer(COMMAND_TIMEOUT, CONNECT_TIMEOUT, RETRY_ATTEMPTS, RETRY_INTERVAL)
                .customize(config);
    }
}
