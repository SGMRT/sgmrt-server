package soma.ghostrunner.domain.pacemaker.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.pacemaker.application.dto.RecoveryContext;
import soma.ghostrunner.domain.pacemaker.application.dto.WorkoutDto;
import soma.ghostrunner.domain.pacemaker.application.dto.WorkoutSetDto;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.domain.PacemakerSet;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerSetRepository;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.List;

/**
 * Pacemaker 복구 준비 서비스
 * - Self-Invocation 문제 방지를 위해 분리
 * - REQUIRES_NEW 트랜잭션으로 독립 실행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PacemakerRecoveryPrepareService {

    private final PacemakerRepository pacemakerRepository;
    private final PacemakerSetRepository pacemakerSetRepository;
    private final MemberService memberService;

    /**
     * 복구 준비: 상태 업데이트 + 필요한 데이터 조회 (독립 트랜잭션)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RecoveryContext prepareForRecovery(Long pacemakerId) {
        log.info("복구 트랜잭션 시작 - pacemakerId={}", pacemakerId);

        // 1. Pacemaker 조회 및 상태 업데이트
        Pacemaker pacemaker = pacemakerRepository.findById(pacemakerId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, pacemakerId));
        pacemaker.updateLastRetryAt();
        pacemaker.proceedForRetry();

        // 2. LLM 호출에 필요한 데이터 준비
        Member member = memberService.findMemberByUuid(pacemaker.getMemberUuid());
        int vdot = memberService.findMemberVdot(member.getUuid());
        WorkoutDto workoutDto = reconstructWorkoutDto(pacemaker);

        log.info("복구 트랜잭션 완료 - pacemakerId={}", pacemakerId);

        return new RecoveryContext(
                member,
                vdot,
                workoutDto,
                pacemaker.getCondition(),
                pacemaker.getTemperature(),
                pacemaker.getId()
        );
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
