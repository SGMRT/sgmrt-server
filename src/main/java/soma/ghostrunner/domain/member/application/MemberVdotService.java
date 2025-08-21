package soma.ghostrunner.domain.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import soma.ghostrunner.domain.member.application.dto.MemberMapper;
import soma.ghostrunner.domain.member.dao.MemberVdotRepository;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.domain.MemberVdot;
import soma.ghostrunner.domain.member.domain.VdotCalculator;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.events.RunFinishedEvent;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberVdotService {

    private final MemberMapper mapper;
    private final MemberVdotRepository memberVdotRepository;
    private final MemberService memberService;

    private final VdotCalculator vdotCalculator;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleRunFinished(RunFinishedEvent event) {
        Member member = findMember(event);
        int vdot = calculateVdot(event.averagePace());
        upsertMemberVdot(member, vdot);
    }

    private Member findMember(RunFinishedEvent event) {
        return memberService.findMemberByUuid(event.memberUuid());
    }

    private int calculateVdot(Double averagePace) {
        double oneMilePace = Running.calculateOneMilePace(averagePace);
        return vdotCalculator.calculateFromPace(oneMilePace);
    }

    private void upsertMemberVdot(Member member, int vdot) {
        Optional<MemberVdot> optionalMemberVdot = memberVdotRepository.findByMemberId(member.getId());
        if (optionalMemberVdot.isPresent()) {
            optionalMemberVdot.get().updateVdot(vdot);
        } else {
            MemberVdot newMemberVdot = mapper.toMemberVdot(member, vdot);
            memberVdotRepository.save(newMemberVdot);
        }
    }

}
