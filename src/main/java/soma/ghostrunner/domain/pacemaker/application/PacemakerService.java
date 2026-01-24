package soma.ghostrunner.domain.pacemaker.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.pacemaker.api.dto.response.PacemakerInCourseViewPollingResponse;
import soma.ghostrunner.domain.pacemaker.api.dto.response.PacemakerPollingResponse;
import soma.ghostrunner.domain.pacemaker.application.dto.WorkoutDto;
import soma.ghostrunner.domain.pacemaker.application.dto.request.PacemakerCreateCommand;
import soma.ghostrunner.domain.pacemaker.application.support.PacemakerApplicationMapper;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.domain.PacemakerSet;
import soma.ghostrunner.domain.pacemaker.domain.RunningType;
import soma.ghostrunner.domain.pacemaker.domain.formula.RunningTipsProvider;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerSetRepository;
import soma.ghostrunner.domain.running.infra.redis.RedisRunningRepository;
import soma.ghostrunner.global.error.ErrorCode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static soma.ghostrunner.global.error.ErrorCode.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PacemakerService {

    private final PacemakerValidator validator;
    private final RunningTipsProvider runningTipsProvider;

    private final PacemakerRepository pacemakerRepository;
    private final PacemakerSetRepository pacemakerSetRepository;
    private final RedisRunningRepository redisRunningRepository;

    private final MemberService memberService;
    private final VdotService runningVdotService;
    private final WorkoutService workoutService;
    private final PacemakerLlmService llmService;

    private final PacemakerApplicationMapper mapper;

    private final String PACEMAKER_API_RATE_LIMIT_KEY_PREFIX = "pacemaker_api_rate_limit:";
    public static final long DAILY_LIMIT = 3;
    private static final int KEY_EXPIRATION_TIME_SECONDS = 86400;

    @Transactional
    public Long createPaceMaker(String memberUuid, PacemakerCreateCommand command) {

        // 멤버 조회 (존재 여부 자동 검증)
        Member member = memberService.findMemberByUuid(memberUuid);

        // 코스 검증
        validator.validateCreationRequest(command.getCourseId());

        // VDOT -> 권장 페이스
        int vdot = determineVdot(member);
        Map<RunningType, Double> expectedPaces = runningVdotService.getExpectedPacesByVdot(vdot);

        // 러닝 타입 + 권장페이스 -> 훈련표 완성
        RunningType runningType = RunningType.toRunningType(command.getType());
        WorkoutDto workoutDto = workoutService.generateWorkouts(command.getTargetDistance(), runningType, expectedPaces);

        // 요청 횟수 제한 : LLM API 요청 트랜잭션으로 넘기기
        String rateLimitKey = handleApiRateLimit(memberUuid, command.getLocalDate());

        // 저장
        Pacemaker pacemaker = savePacemaker(command, command.getCourseId(), runningType, member);
        requestLlmToCreatePacemaker(command, member, workoutDto, vdot, pacemaker, rateLimitKey);
        return pacemaker.getId();
    }

    private int determineVdot(Member member) {
        try {
            return memberService.findMemberVdot(member.getUuid());
        } catch (MemberNotFoundException e) {
            throw new InvalidRunningException(VDOT_NOT_FOUND, "기존 VDOT 기록이 없어 페이스메이커를 생성할 수 없습니다.");
        }
    }

    private String handleApiRateLimit(String memberUuid, LocalDate localDate) {

        String rateLimitKey = createRateLimitKey(memberUuid, localDate);

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

        return rateLimitKey;
    }

    private String createRateLimitKey(String memberUuid, LocalDate localDate) {
        return PACEMAKER_API_RATE_LIMIT_KEY_PREFIX + memberUuid + ":" + localDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private Pacemaker savePacemaker(PacemakerCreateCommand command, Long courseId, RunningType runningType, Member member) {
        return pacemakerRepository.save(mapper.toPacemaker(Pacemaker.Norm.DISTANCE, command, courseId, runningType, member));
    }

    private void requestLlmToCreatePacemaker(PacemakerCreateCommand command, Member member,
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
            return mapper.toPacemakerPollingResponse(pacemaker);
        }

        List<PacemakerSet> pacemakerSets = pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(pacemakerId);
        return mapper.toPacemakerPollingResponse(pacemaker, pacemakerSets, runningTipsProvider.getRandomTip());
    }

    private Pacemaker findPacemaker(Long pacemakerId) {
        return pacemakerRepository.findById(pacemakerId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, pacemakerId));
    }

    @Transactional(readOnly = true)
    public PacemakerInCourseViewPollingResponse getPacemakerInCourse(String memberUuid, Long courseId) {
        Pacemaker pacemaker = findPacemakerInCourse(memberUuid, courseId);
        if (pacemaker.isNotCompleted()) {
            return mapper.toPacemakerInCourseViewPollingResponse(pacemaker);
        }

        List<PacemakerSet> pacemakerSets = pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(pacemaker.getId());
        return mapper.toPacemakerInCourseViewPollingResponse(pacemaker, pacemakerSets);
    }

    private Pacemaker findPacemakerInCourse(String memberUuid, Long courseId) {
        return pacemakerRepository.findByCourseId(courseId, memberUuid)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, courseId + "에 대한 페이스메이커를 찾을 수 없음"));
    }

    @Transactional(readOnly = true)
    public Long getRateLimitCounter(String memberUuid) {
        String rateLimitKey = createRateLimitKey(memberUuid, LocalDate.now(ZoneId.of("Asia/Seoul")));

        String counter = redisRunningRepository.get(rateLimitKey);
        if (counter == null) {
            return DAILY_LIMIT;
        }

        return Math.max(0, DAILY_LIMIT - Long.parseLong(counter));
    }

    @Transactional
    public void updateAfterRunning(String memberUuid, Long pacemakerId, Long runningId) {
        Pacemaker pacemaker = findPacemaker(pacemakerId);
        pacemaker.verifyMember(memberUuid);
        pacemaker.updateAfterRunning(runningId);
    }

    @Transactional
    public void deletePacemaker(String memberUuid, Long pacemakerId) {
        Pacemaker pacemaker = findPacemaker(pacemakerId);
        pacemaker.verifyMember(memberUuid);
        deletePacemakers(pacemakerId);
    }

    private void deletePacemakers(Long pacemakerId) {
        pacemakerRepository.softDelete(pacemakerId);
        pacemakerRepository.softDeleteAllByPacemakerId(pacemakerId);
    }

}
