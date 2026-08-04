package soma.ghostrunner.domain.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.member.application.dto.MemberMapper;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.domain.MemberVdot;
import soma.ghostrunner.domain.member.infra.dao.MemberVdotRepository;
import soma.ghostrunner.domain.pacemaker.application.VdotService;

import java.util.Optional;

/**
 * 러닝 종료에 따른 회원 VDOT 갱신.
 *
 * (구) RunFinishedEvent 리스너(BEFORE_COMMIT)를 직접 호출로 전환한 것 — 같은 트랜잭션 동기 로직은
 * 이벤트의 실질 이득이 없어 호출 흐름이 드러나는 직접 호출로 통일한다. (설계 04 §6)
 * 호출자(러닝 저장 트랜잭션) 안에서 실행되어 원자성이 유지된다.
 */
@Service
@RequiredArgsConstructor
public class MemberVdotUpdater {

    private final MemberMapper mapper;
    private final MemberVdotRepository memberVdotRepository;
    private final MemberService memberService;
    private final VdotService vdotService;

    public void updateVdotFromRun(String memberUuid, Double averagePace) {
        Member member = memberService.findMemberByUuid(memberUuid);
        int vdot = vdotService.calculateVdot(averagePace);
        upsertMemberVdot(member, vdot);
    }

    private void upsertMemberVdot(Member member, int vdot) {
        Optional<MemberVdot> optionalMemberVdot = memberVdotRepository.findByMemberUuid(member.getUuid());
        if (optionalMemberVdot.isPresent()) {
            optionalMemberVdot.get().updateVdot(vdot);
        } else {
            MemberVdot newMemberVdot = mapper.toMemberVdot(member, vdot);
            memberVdotRepository.save(newMemberVdot);
        }
    }
}
