package soma.ghostrunner.domain.pacemaker.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.pacemaker.application.dto.PacemakerCreationResult;
import soma.ghostrunner.domain.pacemaker.application.dto.WorkoutDto;
import soma.ghostrunner.domain.pacemaker.application.dto.request.PacemakerCreateCommand;
import soma.ghostrunner.domain.pacemaker.application.support.PacemakerValidator;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.domain.PacemakerSet;
import soma.ghostrunner.domain.pacemaker.domain.RunningType;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerSetRepository;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;

import java.util.List;
import java.util.Map;

import static soma.ghostrunner.global.error.ErrorCode.VDOT_NOT_FOUND;

/**
 * TX1: Rule-Base Pacemaker 생성 담당
 * - 멤버/코스 검증
 * - VDOT 조회 → 페이스 결정
 * - WorkoutService → Rule-Base 훈련표 생성
 * - Pacemaker(INIT) + PacemakerSet 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PacemakerCreationService {

    private final PacemakerValidator validator;
    private final MemberService memberService;
    private final VdotService vdotService;
    private final WorkoutService workoutService;
    private final PacemakerRepository pacemakerRepository;
    private final PacemakerSetRepository pacemakerSetRepository;

    @Transactional
    public PacemakerCreationResult createInitialPacemaker(String memberUuid, PacemakerCreateCommand command) {

        log.info("TX1 시작: Rule-Base Pacemaker 생성 - memberUuid={}", memberUuid);

        // 1. 멤버 조회 및 검증
        Member member = memberService.findMemberByUuid(memberUuid);

        // 2. 코스 검증
        validator.validateCreationRequest(command.getCourseId());

        // 3. VDOT 조회 → 권장 페이스 결정
        int vdot = determineVdot(member);
        Map<RunningType, Double> expectedPaces = vdotService.getExpectedPacesByVdot(vdot);

        // 4. 러닝 타입 + 권장 페이스 → Rule-Base 훈련표 생성
        RunningType runningType = RunningType.toRunningType(command.getType());
        WorkoutDto workoutDto = workoutService.generateWorkouts(
                command.getTargetDistance(), runningType, expectedPaces);

        // 5. Pacemaker(INIT) 저장
        Pacemaker pacemaker = createAndSavePacemaker(command, runningType, member, workoutDto);

        // 6. PacemakerSet 저장 (message = null)
        List<PacemakerSet> pacemakerSets = PacemakerSet.createRuleBasePacemakerSets(
                workoutDto.getSets(), pacemaker);
        pacemakerSetRepository.saveAll(pacemakerSets);

        log.info("TX1 완료: Pacemaker(INIT) 저장 완료 - pacemakerId={}", pacemaker.getId());

        return PacemakerCreationResult.of(
                pacemaker, member, workoutDto, vdot,
                command.getCondition(), command.getTemperature());
    }

    private int determineVdot(Member member) {
        try {
            return memberService.findMemberVdot(member.getUuid());
        } catch (MemberNotFoundException e) {
            throw new InvalidRunningException(VDOT_NOT_FOUND,
                    "기존 VDOT 기록이 없어 페이스메이커를 생성할 수 없습니다.");
        }
    }

    private Pacemaker createAndSavePacemaker(PacemakerCreateCommand command, RunningType runningType,
                                              Member member, WorkoutDto workoutDto) {
        Pacemaker pacemaker = Pacemaker.createWithRuleBase(
                Pacemaker.Norm.DISTANCE,
                command.getTargetDistance(),
                workoutDto.getExpectedMinutes(),
                command.getCourseId(),
                runningType,
                member.getUuid()
        );
        return pacemakerRepository.save(pacemaker);
    }

}
