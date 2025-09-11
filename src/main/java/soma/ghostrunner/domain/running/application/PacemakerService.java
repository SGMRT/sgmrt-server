package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.running.api.dto.response.PacemakerPollingResponse;
import soma.ghostrunner.domain.running.application.dto.WorkoutDto;
import soma.ghostrunner.domain.running.application.dto.request.CreatePacemakerCommand;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.domain.Pacemaker;
import soma.ghostrunner.domain.running.domain.PacemakerSet;
import soma.ghostrunner.domain.running.domain.RunningType;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerSetRepository;
import soma.ghostrunner.domain.running.infra.redis.RedisRunningRepository;
import soma.ghostrunner.global.error.ErrorCode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static soma.ghostrunner.global.error.ErrorCode.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PacemakerService {

    private final PacemakerRepository pacemakerRepository;
    private final PacemakerSetRepository pacemakerSetRepository;
    private final RedisRunningRepository redisRunningRepository;

    private final MemberService memberService;
    private final RunningVdotService runningVdotService;
    private final WorkoutService workoutService;
    private final PacemakerLlmService llmService;

    private final RunningApplicationMapper mapper;

    private final String PACEMAKER_LOCK_KEY_PREFIX = "pacemaker_api_lock:";
    private final String PACEMAKER_API_RATE_LIMIT_KEY_PREFIX = "pacemaker_api_rate_limit:";

    private static final long LOCK_WAIT_TIME_SECONDS = 0;
    private static final long LOCK_LEASE_TIME_SECONDS = 180;
    private static final long DAILY_LIMIT = 3;
    private static final int KEY_EXPIRATION_TIME_SECONDS = 86400;

    public Long createPaceMaker(String memberUuid, CreatePacemakerCommand command) throws InterruptedException {
        verifyTargetDistanceAtLeast3K(command);

        Member member = memberService.findMemberByUuid(memberUuid);
        int vdot = determineVdot(command, member);

        Map<RunningType, Double> expectedPaces = runningVdotService.getExpectedPacesByVdot(vdot);
        RunningType runningType = RunningType.toRunningType(command.getPurpose());
        WorkoutDto workoutDto = workoutService.generateWorkouts(command.getTargetDistance(), runningType, expectedPaces);

        RLock lock = redisRunningRepository.getLock(PACEMAKER_LOCK_KEY_PREFIX + memberUuid);
        try {
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);

            verifyLockAlreadyGotten(memberUuid, isLocked);
            String rateLimitKey = handleApiRateLimit(memberUuid, command.getLocalDate());

            Pacemaker pacemaker = savePacemaker(command, member);
            requestLlmToCreatePacemaker(command, member, workoutDto, vdot, pacemaker, rateLimitKey);
            return pacemaker.getId();
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

    private String handleApiRateLimit(String memberUuid, LocalDate localDate) {

        String rateLimitKey = PACEMAKER_API_RATE_LIMIT_KEY_PREFIX + memberUuid + ":" + localDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        Long currentCount = redisRunningRepository.incrementRateLimitCounter(
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

        return rateLimitKey;
    }

    private Pacemaker savePacemaker(CreatePacemakerCommand command, Member member) {
        return pacemakerRepository.save(mapper.toPacemaker(Pacemaker.Norm.DISTANCE, command, member));
    }

    private void requestLlmToCreatePacemaker(CreatePacemakerCommand command, Member member,
                                             WorkoutDto workoutDto, int vdot, Pacemaker pacemaker,
                                             String rateLimitKey) {
        llmService.requestLlmToCreatePacemaker(
                member, workoutDto, vdot,
                command.getCondition(), command.getTemperature(), pacemaker.getId(),
                rateLimitKey
        );
    }

    @Transactional(readOnly = true)
    public PacemakerPollingResponse getPacemaker(Long pacemakerId, String memberUuid) {
        Pacemaker pacemaker = findPacemaker(pacemakerId);
        pacemaker.verifyMember(memberUuid);

        if (pacemaker.isNotCompleted()) {
            return mapper.toResponse(pacemaker.getStatus());
        }

        List<PacemakerSet> pacemakerSets = pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(pacemakerId);
        return mapper.toResponse(pacemaker, pacemakerSets);
    }

    private Pacemaker findPacemaker(Long pacemakerId) {
        return pacemakerRepository.findById(pacemakerId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, pacemakerId));
    }

}
