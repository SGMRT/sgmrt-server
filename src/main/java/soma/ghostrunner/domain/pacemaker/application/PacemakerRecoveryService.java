package soma.ghostrunner.domain.pacemaker.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.pacemaker.application.dto.WorkoutDto;
import soma.ghostrunner.domain.pacemaker.application.dto.WorkoutSetDto;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.domain.PacemakerSet;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerSetRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pacemaker 복구 서비스
 * - INIT/PROCEEDING 상태로 남아있는 Pacemaker를 조회하여 LLM 재호출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PacemakerRecoveryService {

    private static final int THRESHOLD_MINUTES = 15;
    private static final int BATCH_SIZE = 10;

    private final PacemakerRepository pacemakerRepository;
    private final PacemakerSetRepository pacemakerSetRepository;
    private final MemberService memberService;
    private final PacemakerLlmService llmService;

    /**
     * 복구 대상 조회 및 LLM 재호출
     */
    @Transactional
    public void recoverStalePacemakers() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(THRESHOLD_MINUTES);
        List<Pacemaker> targets = pacemakerRepository.findRecoveryTargets(
                threshold, PageRequest.of(0, BATCH_SIZE));

        if (targets.isEmpty()) {
            log.debug("복구 대상 Pacemaker 없음");
            return;
        }

        log.info("복구 대상 Pacemaker 발견 - {}건", targets.size());

        for (Pacemaker pacemaker : targets) {
            try {
                recoverPacemaker(pacemaker);
            } catch (Exception e) {
                log.error("Pacemaker 복구 실패 - pacemakerId={}", pacemaker.getId(), e);
            }
        }
    }

    private void recoverPacemaker(Pacemaker pacemaker) {
        log.info("Pacemaker 복구 시작 - pacemakerId={}, status={}", pacemaker.getId(), pacemaker.getStatus());

        // 1. lastRetryAt 업데이트 (중복 처리 방지)
        pacemaker.updateLastRetryAt();

        // 2. INIT 상태면 PROCEEDING으로 전이
        pacemaker.proceedForRetry();

        // 3. LLM 재호출에 필요한 데이터 준비
        Member member = memberService.findMemberByUuid(pacemaker.getMemberUuid());
        int vdot = memberService.findMemberVdot(member.getUuid());
        WorkoutDto workoutDto = reconstructWorkoutDto(pacemaker);

        // 4. 비동기 LLM 재호출 (rateLimitKey = null로 Rate Limit 복구 스킵)
        llmService.requestLlmToCreatePacemaker(
                member,
                workoutDto,
                vdot,
                pacemaker.getCondition(),
                pacemaker.getTemperature(),
                pacemaker.getId(),
                null  // 워커 재시도는 Rate Limit 카운트 안 함
        );

        log.info("Pacemaker 복구 LLM 호출 완료 - pacemakerId={}", pacemaker.getId());
    }

    /**
     * 저장된 PacemakerSet에서 WorkoutDto 복원
     */
    private WorkoutDto reconstructWorkoutDto(Pacemaker pacemaker) {
        List<PacemakerSet> sets = pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(pacemaker.getId());

        List<WorkoutSetDto> workoutSets = sets.stream()
                .map(this::toWorkoutSetDto)
                .toList();

        return WorkoutDto.of(
                pacemaker.getRunningType(),
                pacemaker.getGoalDistance(),
                workoutSets,
                pacemaker.getExpectedTime()
        );
    }

    private WorkoutSetDto toWorkoutSetDto(PacemakerSet set) {
        return WorkoutSetDto.of(
                set.getSetNum(),
                formatPace(set.getPace()),
                set.getStartPoint(),
                set.getEndPoint()
        );
    }

    /**
     * pace (Double, 예: 5.30) → "5:30" 형식으로 변환
     */
    private String formatPace(Double pace) {
        int minutes = pace.intValue();
        int seconds = (int) Math.round((pace - minutes) * 100);
        return String.format("%d:%02d", minutes, seconds);
    }

}
