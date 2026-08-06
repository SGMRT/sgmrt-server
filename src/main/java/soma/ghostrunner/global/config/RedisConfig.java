package soma.ghostrunner.global.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.config.BaseConfig;
import org.redisson.config.Config;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Slf4j
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    /**
     * Redis 커맨드 응답/연결 대기 시간을 코드 기본값으로 못박는다.
     *
     * <p><b>왜 필요한가</b> — 지도 조회는 {@code @Transactional(readOnly = true)} 안에서 Redis를 왕복한다.
     * Redis는 "끊어지는" 대신 "느려지는" 형태로 더 자주 아프다(fork 스톨·이빅션 폭풍·슬로우 커맨드).
     * 응답 대기가 길면 예외가 늦게 도착해 그동안 DB 커넥션을 쥔 채 매달리고, Redis 부분 장애가 전면 장애로 번진다.
     * 대기를 짧게 잘라야 {@code CourseCellCache}의 요청 단위 직행 강등이 실제로 발동한다
     * (설계: docs/design/course-cell-bucket-cache-design.md 결정 2).</p>
     *
     * <p><b>왜 Lettuce가 아니라 Redisson인가</b> — 이 애플리케이션의 {@code RedisConnectionFactory}는
     * {@code RedissonConnectionFactory}다. {@code RedissonAutoConfigurationV2}가
     * {@code @AutoConfiguration(before = RedisAutoConfiguration.class)}로 먼저 등록되어
     * Lettuce 자동 구성({@code @ConditionalOnMissingBean(RedisConnectionFactory.class)})이 아예 건너뛰어진다.
     * 따라서 {@code RedisTemplate}·{@code StringRedisTemplate}·분산락이 모두 <b>같은 Redisson 클라이언트</b>를 쓰며,
     * Lettuce 타임아웃 설정은 이 애플리케이션에서 아무 효과가 없다.</p>
     *
     * <p>이 설정의 영향권은 셀 캐시·구경로 코스 캐시·리프레시 토큰·처리율 제한 Lua로, 전부 <b>단일 고속 커맨드</b>다.
     * 분산락도 같은 클라이언트를 쓰지만 현재 락을 실제로 획득하는 코드는 없다({@code getLock}만 노출).
     * 블로킹 커맨드를 도입한다면 이 기본값을 다시 검토해야 한다.
     * 지원 토폴로지는 single·cluster·sentinel이며, 그 밖의 구성(masterSlave·replicated)에서는 이 보증이 성립하지 않고
     * 부팅 시 {@code error} 로그로 드러난다.</p>
     *
     * <p><b>값의 출처</b> — 프로퍼티 키에서 읽고 <b>기본값만</b> 코드에 둔다(application-*.yml은 전부 gitignore 대상이라
     * 커밋할 수 없다). 운영자는 빌드·jar 재배포 없이 다음 4개 키로 조일 수 있다 — 단 yml은 jar 안이라 편집이 아니라
     * <b>환경변수 오버라이드</b>({@code GHOSTRUNNER_REDIS_RETRY_ATTEMPTS=3} 등)이고, 반영에는 <b>프로세스 재기동이 필요</b>하다
     * ({@code @Value}는 빈 생성 시 1회 해석). 조작 절차는 설계 문서 §8-2 운영 체크리스트 8번.
     * <ul>
     *   <li>{@code spring.data.redis.timeout}(500ms) / {@code spring.data.redis.connect-timeout}(500ms) —
     *       이 둘은 {@code RedissonAutoConfiguration.redisson()}이 먼저 읽어 반영하므로, 자동설정과 이 커스터마이저가
     *       <b>같은 값</b>을 쓰도록 Boot 표준 키를 그대로 쓴다.</li>
     *   <li>{@code ghostrunner.redis.retry-attempts}(1) / {@code ghostrunner.redis.retry-interval}(200ms) —
     *       재시도는 Redisson 고유 개념이라 {@code RedisProperties}에 존재하지 않는 키다.
     *       {@code spring.data.redis.*} 아래에 두면 IDE·메타데이터가 "알 수 없는 속성"으로 표시해 운영자가 오타로 오해하고
     *       지울 수 있고, Boot가 나중에 같은 이름의 키를 도입하면 의미가 조용히 갈라진다. 자체 네임스페이스가 정직하다.</li>
     * </ul>
     * Redisson 기본값은 응답 3s / 연결 10s이며, 이 커스터마이저는 프로퍼티 반영 <b>이후</b> 마지막에 적용된다.</p>
     *
     * <p><b>재시도까지 함께 조여야 의미가 있다</b> — Redisson은 응답 타임아웃 뒤에도 재시도하므로 호출자가 실제로 기다리는
     * 시간은 {@code (timeout + retryInterval) × (retryAttempts + 1)}이다.
     * 기본값(응답 3s + 3회 × 1500ms)이면 타임아웃만 줄여도 응답 없는 Redis에 6초를 매달린다(Testcontainers 실측 6,022ms,
     * 이때 강등은 발동하지 않았다). 재시도는 1회로 줄이되 없애지는 않는다 — 순간적인 블립은 흡수하면서, 진짜 장애는
     * {@code (500 + 200) × 2} = 1.4초 안에 {@code QueryTimeoutException}으로 드러나 강등이 발동한다(같은 조건 실측 1.45s).</p>
     *
     * <p><b>요청당 대기 상한은 커맨드 1회 값의 2배로 잡는다</b> — 위 1.45s는 <b>커맨드 1회</b> 기준이다.
     * 지도 조회 1건이 Redis를 만지는 횟수는 최대 2회다({@code lookup}의 MGET + {@code putAll} 파이프라인).
     * 따라서 <b>요청 단위 대기 상한은 최대 2왕복 ~2.9s</b>이며, HikariCP 풀 산정에는 이 값을 써야 한다(1.45s가 아니다).
     * degraded로 빠지면 {@code putAll}에 도달하지 않으므로, 최악은 MGET은 성공하고 그 직후에 느려진 경우다.</p>
     *
     * <p><b>재시도 축소가 안전한 이유 — idempotency</b> — Redisson은 응답 타임아웃 뒤 <b>idempotent 커맨드만</b> 재시도하고,
     * non-idempotent 커맨드(SET·DEL·EVAL)는 재시도 없이 실패시킨다({@code RedisExecutor}의
     * {@code "retry attempts, is non-idempotent command"}). 따라서 재시도 축소가 리프레시 토큰 저장이나
     * 처리율 제한 Lua의 <b>중복 실행을 만들 수 없다</b>(= 중복 차감 없음). 확인에 반나절이 드는 사실이라 여기 적어 둔다.</p>
     *
     * <p><b>대신 무엇을 잃는가</b> — {@code retryInterval × retryAttempts}(= "아직 전송하지 못한 커맨드"의 흡수 창)가
     * 4,500ms → 200ms로 줄었다(22.5배). TCP 재전송(리눅스 최소 RTO 200ms)이나 커넥션 재수립 구간이 여기 걸리면,
     * <b>강등 경로가 없는 소비자</b>는 그 요청이 5xx가 된다 —
     * {@code RefreshTokenService}(→ {@code AuthService} 로그인·재발급이 그대로 전파),
     * {@code PacemakerRateLimitService.incrementRateLimitCounter}(증가 경로에는 {@code @Retryable}이 없다).
     * 변경 전에도 결과는 5xx였고 소요가 ~7.5s → ~1.4s로 줄었을 뿐이지만 <b>빈도는 늘 수 있다.</b>
     * 배포 후 auth 5xx 비율을 함께 관측하고, 늘면 {@code ghostrunner.redis.retry-attempts}를 2~3으로 되돌린다
     * (대가: 지도 조회의 강등 발동이 그만큼 늦어진다). {@code PushIdempotencyService}는
     * {@code catch (Exception)} → {@code LOCK_ACQUIRED} fail-open이라 영향이 없고, 셀 캐시는 직행 강등 경로가 있다.</p>
     */
    @Bean
    public RedissonAutoConfigurationCustomizer redisTimeoutCustomizer(
            @Value("${spring.data.redis.timeout:500ms}") Duration commandTimeout,
            @Value("${spring.data.redis.connect-timeout:500ms}") Duration connectTimeout,
            @Value("${ghostrunner.redis.retry-attempts:1}") int retryAttempts,
            @Value("${ghostrunner.redis.retry-interval:200ms}") Duration retryInterval) {
        return config -> {
            BaseConfig<?> serverConfig = serverConfigOf(config);
            if (serverConfig == null) {
                // masterSlave/replicated 등 미지원 토폴로지 — 기동은 깨뜨리지 않되, 보증이 깨졌음을 크게 남긴다.
                log.error("RedisConfig - unsupported Redisson topology, Redis timeout/retry defaults NOT applied. "
                        + "지도 조회의 요청 단위 직행 강등 보증(설계 결정 2)이 성립하지 않는다. 구성을 확인할 것.");
                return;
            }
            serverConfig.setTimeout((int) commandTimeout.toMillis());
            serverConfig.setConnectTimeout((int) connectTimeout.toMillis());
            serverConfig.setRetryAttempts(retryAttempts);
            serverConfig.setRetryInterval((int) retryInterval.toMillis());
        };
    }

    /**
     * 단일·클러스터·센티널 어느 토폴로지든 같은 타임아웃 정책을 적용한다.
     * {@code use*Servers()}는 해당 모드일 때만 호출해야 한다 — 다른 모드에서 부르면 {@code IllegalStateException}이고,
     * 그걸 커스터마이저 안에서 던지면 애플리케이션이 아예 뜨지 않는다.
     * {@code useMasterSlaveServers()}/{@code useReplicatedServers()}는 {@code Config}에 대응하는 판별자가 없어
     * 안전하게 감지할 수 없으므로 추가하지 않는다 — 그 두 경우는 호출부의 {@code error} 분기로 간다.
     */
    private static BaseConfig<?> serverConfigOf(Config config) {
        if (config.isSingleConfig())   return config.useSingleServer();
        if (config.isClusterConfig())  return config.useClusterServers();
        if (config.isSentinelConfig()) return config.useSentinelServers();
        return null;
    }

}
