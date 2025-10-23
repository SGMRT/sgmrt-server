package soma.ghostrunner.domain.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import soma.ghostrunner.domain.member.application.dto.MemberMapper;
import soma.ghostrunner.domain.member.infra.dao.MemberVdotRepository;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.domain.MemberVdot;
import soma.ghostrunner.domain.running.application.RunningVdotService;
import soma.ghostrunner.domain.running.domain.events.RunFinishedEvent;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RunFinishedEventListener {

    private final MemberMapper mapper;
    private final MemberVdotRepository memberVdotRepository;
    private final MemberService memberService;
    private final RunningVdotService runningVdotService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleRunFinished(RunFinishedEvent event) {
        Member member = memberService.findMemberByUuid(event.memberUuid());
        int vdot = runningVdotService.calculateVdot(event.averagePace());
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
