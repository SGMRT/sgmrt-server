package soma.ghostrunner.domain.pacemaker.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.domain.running.infra.redis.RedisRunningRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static soma.ghostrunner.global.error.ErrorCode.TOO_MANY_REQUESTS;

/**
 * 페이스메이커 Rate Limit 관리 서비스
 * - 일일 사용량 조회
 * - 일일 사용량 증가
 * - Rate Limit 키 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PacemakerRateLimitService {

    private final RedisRunningRepository redisRunningRepository;

    private static final String PACEMAKER_API_RATE_LIMIT_KEY_PREFIX = "pacemaker_api_rate_limit:";
    public static final long DAILY_LIMIT = 3;
    private static final int KEY_EXPIRATION_TIME_SECONDS = 86400;

    /**
     * Rate Limit 사전 검증 (Fail-Fast)
     * TX 시작 전에 호출하여 불필요한 DB 작업 방지
     *
     * @throws InvalidRunningException 일일 사용량 초과 시
     */
    public void validateRateLimit(String memberUuid) {
        Long remaining = getRemainingCount(memberUuid);
        if (remaining <= 0) {
            log.warn("Rate Limit 사전 체크 실패 - memberUuid={}, remaining={}", memberUuid, remaining);
            throw new InvalidRunningException(TOO_MANY_REQUESTS, "일일 사용량을 초과했습니다.");
        }
        log.debug("Rate Limit 사전 체크 통과 - memberUuid={}, remaining={}", memberUuid, remaining);
    }

    /**
     * 남은 일일 사용량 조회
     */
    public Long getRemainingCount(String memberUuid) {
        String rateLimitKey = createRateLimitKey(memberUuid);
        String counter = redisRunningRepository.get(rateLimitKey);

        if (counter == null) {
            return DAILY_LIMIT;
        }

        return Math.max(0, DAILY_LIMIT - Long.parseLong(counter));
    }

    /**
     * Rate Limit 카운터 증가 (사용량 소비)
     * @throws InvalidRunningException 일일 사용량 초과 시
     */
    public void incrementCounter(String memberUuid) {
        String rateLimitKey = createRateLimitKey(memberUuid);
        incrementRateLimitCounter(memberUuid, rateLimitKey);
    }

    /**
     * Rate Limit 카운터 감소 (LLM 실패 시 보상)
     */
    public void decrementCounter(String rateLimitKey) {
        redisRunningRepository.decrementRateLimitCounter(rateLimitKey);
        log.info("Rate Limit 카운트 보상 완료 - rateLimitKey={}", rateLimitKey);
    }

    /**
     * 현재 멤버의 Rate Limit 키 생성
     */
    public String createRateLimitKey(String memberUuid) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        return PACEMAKER_API_RATE_LIMIT_KEY_PREFIX + memberUuid + ":" + today.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private void incrementRateLimitCounter(String memberUuid, String rateLimitKey) {
        Long currentCount = redisRunningRepository.incrementRateLimitCounter(
                rateLimitKey,
                DAILY_LIMIT,
                KEY_EXPIRATION_TIME_SECONDS
        );

        if (currentCount == null) {
            log.error("처리율 제한을 위한 스크립트 처리중 에러 발생");
            throw new RuntimeException("Redis 스크립트 실행 오류가 발생했습니다.");
        }

        if (currentCount == -1) {
            log.warn("사용자 ID '{}'가 일일 사용량({})을 초과했습니다.", memberUuid, DAILY_LIMIT);
            throw new InvalidRunningException(TOO_MANY_REQUESTS, "일일 사용량을 초과했습니다.");
        }

        log.info("Rate Limit 카운트 증가 완료 - rateLimitKey={}, currentCount={}", rateLimitKey, currentCount);
    }

}
