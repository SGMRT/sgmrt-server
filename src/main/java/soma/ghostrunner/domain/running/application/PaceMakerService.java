package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.running.application.dto.request.CreatePacemakerCommand;
import soma.ghostrunner.domain.running.domain.RunningType;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.domain.running.infra.RedisDistributedLockManager;
import soma.ghostrunner.domain.running.infra.RedisRateLimiterRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static soma.ghostrunner.global.error.ErrorCode.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaceMakerService {

    private final RedisRateLimiterRepository redisRateLimiterRepository;
    private final RedisDistributedLockManager redisDistributedLockManager;

    private final MemberService memberService;
    private final RunningVdotService runningVdotService;

    private final RestTemplate restTemplate;
    private final String MOCK_API_URL = "http://0.0.0.0:3000/llm-test";
    private final WebClient webClient;

    private static final String PACEMAKER_LOCK_KEY_PREFIX = "pacemaker_api_lock:";
    private static final String PACEMAKER_API_RATE_LIMIT_KEY_PREFIX = "pacemaker_api_rate_limit:";

    private static final long LOCK_WAIT_TIME_SECONDS = 0;
    private static final long LOCK_LEASE_TIME_SECONDS = 60;
    private static final long DAILY_LIMIT = 3;
    private static final int KEY_EXPIRATION_TIME_SECONDS = 86400;

    public void createPaceMaker(String memberUuid, CreatePacemakerCommand command) throws InterruptedException {
        Member member = memberService.findMemberByUuid(memberUuid);
        int vdot = determineVdot(command, member);

        verifyTargetDistanceAtLeast3K(command);
        Map<RunningType, Double> expectedPaces = runningVdotService.getExpectedPaces(vdot);
        RunningType runningType = RunningType.convertToRunningType(command.getPurpose());
        // 러닝 유형 -> 해당 유형의 모든 훈련표
        // 모든 훈련표 + 권장 페이스 -> 거리계산 -> 가장 가까운 거리합의 훈련표
        // 훈련표의 각 세트 거리 * ( 목표 거리 / 거리합 )

        RLock lock = redisDistributedLockManager.getLock(PACEMAKER_LOCK_KEY_PREFIX + memberUuid);
        try {
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
            verifyLockAlreadyGotten(memberUuid, isLocked);
            handleApiRateLimit(memberUuid, command.getLocalDate());
            handleLlmApiRequest(memberUuid);
        } finally {
            lock.unlock();
        }
    }

    private void verifyTargetDistanceAtLeast3K(CreatePacemakerCommand command) {
        final double MINIMUM_DISTANCE_KM = 3.0;
        if (command.getTargetDistance() < MINIMUM_DISTANCE_KM) {
            throw new IllegalStateException("3km 미만의 거리는 페이스메이커를 생성할 수 없습니다.");
        }
    }

    private int determineVdot(CreatePacemakerCommand command, Member member) {
        if (command.getPacePerKm() != null) {
            return runningVdotService.calculateVdot(command.getPacePerKm());
        }
        try {
            return memberService.findMemberVdot(member).getVdot();
        } catch (MemberNotFoundException e) {
            throw new InvalidRunningException(VDOT_NOT_FOUND, "기존 VDOT 기록이 없어 페이스메이커를 생성할 수 없습니다.");
        }
    }

    private void verifyLockAlreadyGotten(String memberUuid, boolean isLocked) {
        if (!isLocked) {
            log.warn("사용자 UUID '{}'의 락 획득 실패. 이미 다른 요청이 처리 중입니다.", memberUuid);
            throw new IllegalStateException("이미 다른 요청이 처리 중입니다.");
        }
    }

    private void handleApiRateLimit(String memberUuid, LocalDate localDate) {
        String rateLimitKey = PACEMAKER_API_RATE_LIMIT_KEY_PREFIX + memberUuid + ":" + localDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        Long currentCount = redisRateLimiterRepository.incrementAndGet(
                rateLimitKey,
                DAILY_LIMIT,
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
        Thread.sleep(3000);
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
