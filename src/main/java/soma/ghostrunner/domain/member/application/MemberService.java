package soma.ghostrunner.domain.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.member.*;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberBioInfo;
import soma.ghostrunner.domain.member.MemberNotFoundException;
import soma.ghostrunner.domain.member.MemberRepository;
import soma.ghostrunner.domain.member.api.dto.ProfileImageUploadRequest;
import soma.ghostrunner.domain.member.application.dto.MemberCreationRequest;
import soma.ghostrunner.domain.member.dao.MemberAuthInfoRepository;
import soma.ghostrunner.domain.member.dao.TermsAgreementRepository;
import soma.ghostrunner.domain.member.domain.MemberAuthInfo;
import soma.ghostrunner.domain.member.domain.TermsAgreement;
import soma.ghostrunner.global.error.ErrorCode;

import static soma.ghostrunner.global.error.ErrorCode.MEMBER_ALREADY_EXISTED;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final TermsAgreementRepository termsAgreementRepository;
    private final MemberAuthInfoRepository memberAuthInfoRepository;

    @Transactional(readOnly = true)
    public Member findMemberByUuid(String uuid) {
        return memberRepository.findByUuid(uuid)
                .orElseThrow(() -> new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND, "cannot find member uuid: " + uuid));
    }

    public String findUuidByAuthUid(String authUid) {
        return memberAuthInfoRepository.findMemberUuidByExternalAuthUid(authUid)
                .orElseThrow(() -> new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND));
    }

    public void verifyMemberExistsByAuthUid(String authUid) {
        boolean isExist = memberAuthInfoRepository.existsByExternalAuthUid(authUid);
        if (isExist) {
            throw new InvalidMemberException(MEMBER_ALREADY_EXISTED, "이미 존재하는 회원인 경우");
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
        verifyAlreadyExistNickname(creationRequest);
        member = memberRepository.save(member);

        MemberAuthInfo memberAuthInfo = MemberAuthInfo.of(member, creationRequest.getExternalAuthId());
        memberAuthInfoRepository.save(memberAuthInfo);

        member = memberRepository.save(member);

        TermsAgreement termsAgreement = creationRequest.getTermsAgreement();
        if (termsAgreement != null) {
            termsAgreement.setMember(member);
            termsAgreementRepository.save(termsAgreement);
        }

        return member;
    }

    private void verifyAlreadyExistNickname(MemberCreationRequest creationRequest) {
        boolean alreadyExist = memberRepository.existsByNickname(creationRequest.getNickname());
        if (alreadyExist) {
            throw new InvalidMemberException(ErrorCode.NICKNAME_ALREADY_EXIST, "이미 존재하는 닉네임인 경우");
        }
    }

    // todo 컨트롤러에서 enum 검증으로 변경
    private boolean isAllowedImageContentType(String contentType) {
        return "image/jpeg".equalsIgnoreCase(contentType) ||
                "image/png".equalsIgnoreCase(contentType);
    }

    // 파일 시스템 및 URL 인코딩에서 허용되지 않는 문자 대체
    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[/\\\\?%*:|\"<>]", "_").trim();
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1);
        }
        return "";
    }

}
