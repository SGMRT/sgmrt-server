package soma.ghostrunner.domain.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberBioInfo;
import soma.ghostrunner.domain.member.MemberNotFoundException;
import soma.ghostrunner.domain.member.MemberRepository;
import soma.ghostrunner.domain.member.application.dto.MemberCreationRequest;
import soma.ghostrunner.domain.member.dao.TermsAgreementRepository;
import soma.ghostrunner.domain.member.domain.TermsAgreement;
import soma.ghostrunner.global.common.error.ErrorCode;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final TermsAgreementRepository termsAgreementRepository;

    @Transactional(readOnly = true)
    public Member findMemberById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND, id));
    }

    public Member findMemberByAuthUid(String authUid) {
        return memberRepository.findByExternalAuthUid(authUid)
                .orElseThrow(() -> new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND));
    }

    public boolean isMemberExistsByAuthUid(String firebaseUid) {
        return memberRepository.existsByExternalAuthUid(firebaseUid);
    }

    @Transactional
    public Member createMember(MemberCreationRequest creationRequest) {
        Member member = Member.builder()
                .nickname(creationRequest.getNickname())
                .uuid(creationRequest.getUuid())
                .externalAuthUid(creationRequest.getExternalAuthId())
                .bioInfo(new MemberBioInfo(creationRequest.getGender(),
                                           creationRequest.getWeight(),
                                           creationRequest.getHeight()))
                .profilePictureUrl(creationRequest.getProfileImageUrl())
                .build();

        TermsAgreement termsAgreement = creationRequest.getTermsAgreement();
        if (termsAgreement != null) {
            termsAgreement.setMember(member);
            termsAgreementRepository.save(termsAgreement);
        }

        return memberRepository.save(member);
    }
}
