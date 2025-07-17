package soma.ghostrunner.domain.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberBioInfo;
import soma.ghostrunner.domain.member.MemberNotFoundException;
import soma.ghostrunner.domain.member.MemberRepository;
import soma.ghostrunner.domain.member.application.dto.MemberCreationRequest;
import soma.ghostrunner.domain.member.dao.MemberAuthInfoRepository;
import soma.ghostrunner.domain.member.dao.TermsAgreementRepository;
import soma.ghostrunner.domain.member.domain.MemberAuthInfo;
import soma.ghostrunner.domain.member.domain.TermsAgreement;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final TermsAgreementRepository termsAgreementRepository;
    private final MemberAuthInfoRepository memberAuthInfoRepository;

    @Transactional(readOnly = true)
    public Member findMemberById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND, id));
    }

    public String findUuidByAuthUid(String authUid) {
        return memberAuthInfoRepository.findMemberUuidByExternalAuthUid(authUid)
                .orElseThrow(() -> new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND));
    }

    public void verifyMemberExistsByAuthUid(String authUid) {
        boolean isExist = memberAuthInfoRepository.existsByExternalAuthUid(authUid);
        if (isExist) {
            throw new IllegalArgumentException("이미 존재하는 사용자");
        }
    }

    @Transactional
    public Member createMember(MemberCreationRequest creationRequest) {
        Member member = Member.builder()
                .nickname(creationRequest.getNickname())
                .bioInfo(new MemberBioInfo(creationRequest.getGender(),
                                           creationRequest.getWeight(),
                                           creationRequest.getHeight()))
                .profilePictureUrl(creationRequest.getProfileImageUrl())
                .build();
        member = memberRepository.save(member);

        MemberAuthInfo memberAuthInfo = MemberAuthInfo.of(member, creationRequest.getExternalAuthId());
        memberAuthInfoRepository.save(memberAuthInfo);

        TermsAgreement termsAgreement = creationRequest.getTermsAgreement();
        if (termsAgreement != null) {
            termsAgreement.setMember(member);
            termsAgreementRepository.save(termsAgreement);
        }

        return member;
    }
}
