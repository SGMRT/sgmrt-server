package soma.ghostrunner.domain.running.application;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.domain.MemberVdot;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static soma.ghostrunner.global.error.ErrorCode.*;

@Slf4j
@Service
public class PaceMakerService {

    private final RedissonClient redissonClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> rateLimiterScript;
    private final MemberService memberService;

    private final RestTemplate restTemplate;
    private final String MOCK_API_URL = "http://0.0.0.0:3000/llm-test";
    private final WebClient webClient;

    public PaceMakerService(RedissonClient redissonClient, RedisTemplate<String, String> redisTemplate,
                            MemberService memberService, RestTemplate restTemplate, WebClient webClient) {
        this.redissonClient = redissonClient;
        this.redisTemplate = redisTemplate;
        this.rateLimiterScript = new DefaultRedisScript<>();
        this.rateLimiterScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("rate-limiter.lua")));
        this.rateLimiterScript.setResultType(Long.class);
        this.memberService = memberService;
        this.restTemplate = restTemplate;
        this.webClient = webClient;
    }

    private static final long LOCK_WAIT_TIME_SECONDS = 0;
    private static final long LOCK_LEASE_TIME_SECONDS = 60;
    private static final long DAILY_LIMIT = 3;
    private static final String KEY_EXPIRATION_TIME_SECONDS = String.valueOf(86400);

    public void createPaceMaker(String memberUuid, LocalDate localDate) throws InterruptedException {
        // 사용자 & VDOT
        Member member = memberService.findMemberByUuid(memberUuid);
        MemberVdot memberVdot = memberService.findMemberVdot(member);

        // 러닝 목적 -> 러닝 유형
        // VDOT + 러닝 유형 + 목표 거리 -> 훈련표

        // Lock
        String lockKey = "pacemaker_api_lock:" + memberUuid;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
            verifyLockAlreadyGotten(memberUuid, isLocked);
            handleApiRateLimit(memberUuid, localDate);
            handleLlmApiRequest(memberUuid);
        } finally {
            lock.unlock();
        }
    }

    private void verifyLockAlreadyGotten(String memberUuid, boolean isLocked) {
        if (!isLocked) {
            log.warn("사용자 UUID '{}'의 락 획득 실패. 이미 다른 요청이 처리 중입니다.", memberUuid);
            throw new IllegalStateException("이미 다른 요청이 처리 중입니다.");
        }
    }

    private void handleApiRateLimit(String memberUuid, LocalDate localDate) {
        String rateLimitKey = "ratelimit:" + memberUuid + ":" + localDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        Long currentCount = redisTemplate.execute(
                rateLimiterScript,
                Collections.singletonList(rateLimitKey),
                String.valueOf(DAILY_LIMIT),
                KEY_EXPIRATION_TIME_SECONDS
        );

        if (currentCount == null) {
            log.error("처리율 제한을 위한 스크립트 처리중 에러 발생");
            throw new RuntimeException("Redis 스크립트 실행 오류가 발생했습니다.");
        }

        if (currentCount > DAILY_LIMIT) {
            log.warn("사용자 ID '{}'가 일일 사용량({})을 초과했습니다.", memberUuid, DAILY_LIMIT);
            throw new InvalidRunningException(TOO_MANY_REQUESTS, "일일 사용량을 초과했습니다.");
        }
    }

    private void handleLlmApiRequest(String memberUuid) throws InterruptedException {
        Thread.sleep(1000);
    }

    public String callSync() {
        log.info("동기 호출 시작: {}", Thread.currentThread().getName());
        String result = restTemplate.getForObject(MOCK_API_URL, String.class);
        log.info("동기 호출 완료: {}", Thread.currentThread().getName());
        return result;
    }

    @Async
    public CompletableFuture<String> callAsync(Long startTime) {
        log.info("비동기 호출 시작: {}", Thread.currentThread().getName());
        String response = restTemplate.getForObject(MOCK_API_URL, String.class);
        log.info("비동기 호출 완료: {}", Thread.currentThread().getName());
        log.info("비동기 응답시간 : {}", System.currentTimeMillis() - startTime);
        return CompletableFuture.completedFuture(response);
    }

    public void callMockApiNonBlocking(Long startTime) {
        webClient.get()
                .uri("/llm-test")
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                        // onNext: 응답이 성공적으로 왔을 때 실행될 로직
                        response -> {
                            log.info("논블로킹 호출 완료: {}", Thread.currentThread().getName());
                            log.info("논블로킹 응답시간 : {}", System.currentTimeMillis() - startTime);
                        },
                        // onError: 오류가 발생했을 때 실행될 로직
                        error -> {
                            log.info("논블로킹 호출 실패: {}", Thread.currentThread().getName());
                            log.info("논블로킹 응답시간 : {}", System.currentTimeMillis() - startTime);
                        }
                );
    }

}
