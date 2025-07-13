package soma.ghostrunner.domain.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberNotFoundException;
import soma.ghostrunner.domain.member.MemberRepository;
import soma.ghostrunner.global.common.error.ErrorCode;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public Member findMemberById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND, id));
    }

    public Member findMemberByAuthUid(String authUid) {
        return memberRepository.findByAuthUid(authUid)
                .orElseThrow(() -> new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND));
    }

    public boolean isMemberExistsByAuthUid(String firebaseUid) {
        return memberRepository.existsByAuthUid(firebaseUid);
    }
}
