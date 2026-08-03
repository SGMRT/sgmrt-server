package soma.ghostrunner.domain.pacemaker.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.pacemaker.application.dto.WorkoutDto;
import soma.ghostrunner.domain.pacemaker.application.support.PacemakerApplicationMapper;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.domain.PacemakerSet;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerSetRepository;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PacemakerLlmCallbackService {

    private final ApplicationEventPublisher eventPublisher;
    private final PacemakerApplicationMapper mapper;
    private final PacemakerRateLimitService rateLimitService;

    private final PacemakerRepository pacemakerRepository;
    private final PacemakerSetRepository pacemakerSetRepository;

    @Transactional
    public void handleSuccess(Long pacemakerId, String workoutDtoStr) {

        WorkoutDto workoutDto = WorkoutDto.fromVoiceGuidanceGeneratedWorkoutDto(workoutDtoStr);
        Pacemaker pacemaker = findPacemaker(pacemakerId);

        // 멱등성: 이미 완료된 상태면 무시
        if (pacemaker.isCompleted()) {
            log.warn("이미 완료된 Pacemaker 무시 - pacemakerId={}, status={}", pacemakerId, pacemaker.getStatus());
            return;
        }

        // 상태 전이: PROCEEDING -> COMPLETED
        pacemaker.complete(
                workoutDto.getSummary(),
                workoutDto.getGoalKm(),
                workoutDto.getExpectedMinutes(),
                workoutDto.getInitialMessage()
        );

        // 기존 PacemakerSet의 message 업데이트
        List<PacemakerSet> existingSets = pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(pacemakerId);
        updatePacemakerSetMessages(existingSets, workoutDto);

        eventPublisher.publishEvent(mapper.toPacemakerCreatedEvent(pacemaker));
    }

    private void updatePacemakerSetMessages(List<PacemakerSet> existingSets, WorkoutDto workoutDto) {
        var workoutSets = workoutDto.getSets();
        for (int i = 0; i < existingSets.size() && i < workoutSets.size(); i++) {
            existingSets.get(i).updateMessage(workoutSets.get(i).getMessage());
        }
    }

    private Pacemaker findPacemaker(Long pacemakerId) {
        return pacemakerRepository.findById(pacemakerId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, pacemakerId));
    }

    /**
     * LLM 실패 시 FALLBACK 처리 (메인 요청용)
     * - Redis 카운트 복구 (보상) - 사용자가 다시 시도할 수 있도록
     * - FALLBACK 상태로 전환하여 Rule-Base 결과 제공
     */
    @Transactional
    public void handleError(String rateLimitKey, Long pacemakerId) {
        Pacemaker pacemaker = findPacemaker(pacemakerId);

        // 멱등성: 이미 완료된 상태면 무시
        if (pacemaker.isCompleted()) {
            log.warn("이미 완료된 Pacemaker 무시 - pacemakerId={}, status={}", pacemakerId, pacemaker.getStatus());
            return;
        }

        // Redis 카운트 복구 (보상) - rateLimitKey가 null이면 스킵 (워커 재시도)
        if (rateLimitKey != null) {
            rateLimitService.decrementCounter(rateLimitKey);
        }

        // 상태 전이: PROCEEDING -> FALLBACK
        pacemaker.fallback();

        // PacemakerSet은 이미 TX1에서 저장되어 있으므로 추가 작업 불필요
        // message만 null인 상태로 Rule-Base 결과 제공
        log.info("FALLBACK 처리 완료 - pacemakerId={}", pacemakerId);
    }

}
