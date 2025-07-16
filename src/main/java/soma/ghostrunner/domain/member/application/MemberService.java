package soma.ghostrunner.domain.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberBioInfo;
import soma.ghostrunner.domain.member.MemberNotFoundException;
import soma.ghostrunner.domain.member.MemberRepository;
import soma.ghostrunner.domain.member.api.dto.ProfileImageUploadRequest;
import soma.ghostrunner.domain.member.application.dto.MemberCreationRequest;
import soma.ghostrunner.domain.member.dao.TermsAgreementRepository;
import soma.ghostrunner.domain.member.domain.TermsAgreement;
import soma.ghostrunner.clients.aws.S3PresignProvider;
import soma.ghostrunner.global.common.error.ErrorCode;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final S3PresignProvider s3PresignProvider;
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

    public String generateProfileImageUploadUrl(String memberUuid, ProfileImageUploadRequest request) {
        // todo: memberuuid가 현재 로그인한 사용자인지 확인
        // todo: content-type 검증 로직 Enum으로 변경
        if (!isAllowedImageContentType(request.getContentType())) {
            throw new IllegalArgumentException("부적절한 Content-Type입니다: " + request.getContentType());
        }

        String cleansedFilename = sanitizeFilename(request.getFilename());
        String fileExt = getFileExtension(cleansedFilename);
        String objectKey = String.format("profiles/%s/%s.%s",
                memberUuid, UUID.randomUUID(), fileExt);

        return s3PresignProvider.generatePresignedPutUrl(objectKey, request.getContentType(), Duration.ofMinutes(5));
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
