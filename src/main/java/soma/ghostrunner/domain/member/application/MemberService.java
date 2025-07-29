package soma.ghostrunner.domain.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.member.api.dto.TermsAgreementDto;
import soma.ghostrunner.domain.member.api.dto.request.MemberSettingsUpdateRequest;
import soma.ghostrunner.domain.member.api.dto.request.MemberUpdateRequest;
import soma.ghostrunner.domain.member.api.dto.response.MemberResponse;
import soma.ghostrunner.domain.member.dao.MemberSettingsRepository;
import soma.ghostrunner.domain.member.domain.*;
import soma.ghostrunner.domain.member.enums.Gender;
import soma.ghostrunner.domain.member.exception.InvalidMemberException;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.member.dao.MemberRepository;
import soma.ghostrunner.domain.member.api.dto.request.ProfileImageUploadRequest;
import soma.ghostrunner.domain.member.application.dto.MemberCreationRequest;
import soma.ghostrunner.domain.member.dao.MemberAuthInfoRepository;
import soma.ghostrunner.domain.member.dao.TermsAgreementRepository;
import soma.ghostrunner.clients.aws.S3PresignProvider;
import soma.ghostrunner.domain.member.exception.MemberSettingsNotFoundException;
import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.BusinessException;

import static soma.ghostrunner.global.error.ErrorCode.MEMBER_ALREADY_EXISTED;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final S3PresignProvider s3PresignProvider;
    private final MemberRepository memberRepository;
    private final TermsAgreementRepository termsAgreementRepository;
    private final MemberAuthInfoRepository memberAuthInfoRepository;
    private final MemberSettingsRepository memberSettingsRepository;

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

    @Transactional(readOnly = true)
    public MemberResponse findMemberDtoByUuid(String uuid) {
        return memberRepository.findMemberDtoByUuid(uuid)
                .orElseThrow(() -> new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND, "cannot find member uuid: " + uuid));
    }

    @Transactional
    public void updateMember(String uuid, MemberUpdateRequest request) {
        Member member = findMemberByUuid(uuid);

        Gender gender = member.getBioInfo().getGender();
        Integer weight = member.getBioInfo().getWeight();
        Integer height = member.getBioInfo().getHeight();
        for(MemberUpdateRequest.UpdatedAttr attr : request.getUpdateAttrs()) {
            switch(attr) {
                case NICKNAME:
                    verifyNickname(request.getNickname());
                    member.updateNickname(request.getNickname());
                    break;
                case GENDER:
                    verifyGender(request.getGender());
                    gender = request.getGender();
                    break;
                case HEIGHT:
                    verifyHeight(request.getHeight());
                    height = request.getHeight();
                    break;
                case WEIGHT:
                    verifyWeight(request.getHeight());
                    weight = request.getWeight();
                    break;
            }
        }

        member.updateBioInfo(gender, weight, height);
    }

    private void verifyNickname(String nickname) {
        if(nickname == null) throw new IllegalArgumentException("nickname cannot be null");
        checkNicknameDuplication(nickname);
    }

    private void verifyGender(Gender gender) {
        if(gender == null) throw new IllegalArgumentException("gender cannot be null");
    }

    private void verifyHeight(Integer height) {
        // height와 weight는 null 입력 허용
        int MAX_HEIGHT = 300, MIN_HEIGHT = 50;
        if(height != null && (height >= MAX_HEIGHT || height <= MIN_HEIGHT)) throw new IllegalArgumentException("invalid height");
    }

    private void verifyWeight(Integer weight) {
        // height와 weight는 null 입력 허용
        int MAX_WEIGHT = 300, MIN_WEIGHT = 20;
        if(weight != null && (weight >= MAX_WEIGHT || weight <= MIN_WEIGHT)) throw new IllegalArgumentException("invalid weight");
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
        verifyNickname(creationRequest.getNickname());
        member = memberRepository.save(member);
        saveInitialMemberDetails(member, creationRequest.getExternalAuthId(), creationRequest.getTermsAgreement());
        return member;
    }

    private void saveInitialMemberDetails(Member member, String externalAuthId, TermsAgreement termsAgreement) {
        if (member == null || externalAuthId == null || termsAgreement == null) throw new IllegalArgumentException();

        MemberAuthInfo memberAuthInfo = MemberAuthInfo.of(member, externalAuthId);
        memberAuthInfoRepository.save(memberAuthInfo);

        saveTermsAgreement(member,  termsAgreement);

        MemberSettings memberSettings = MemberSettings.of(member);
        memberSettingsRepository.save(memberSettings);
    }

    private void checkNicknameDuplication(String nickname) {
        boolean alreadyExist = memberRepository.existsByNickname(nickname);
        if (alreadyExist) {
            throw new InvalidMemberException(ErrorCode.NICKNAME_ALREADY_EXIST, "이미 존재하는 닉네임인 경우");
        }
    }

    @Transactional
    public void updateMemberSettings(String memberUuid, MemberSettingsUpdateRequest request) {
        MemberSettings currentSettings = memberSettingsRepository.findByMember_Uuid(memberUuid)
                .orElseThrow(MemberSettingsNotFoundException::new);

        currentSettings.updateSettings(request.getPushAlarmEnabled(),
                request.getVibrationEnabled());
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

    @Transactional
    public void saveTermsAgreement(String memberUuid, TermsAgreementDto termsAgreementDto) {
        Member member = findMemberByUuid(memberUuid);
        if(termsAgreementDto == null) throw new IllegalArgumentException("termsAgreement cannot be null");
        TermsAgreement agreement = TermsAgreement.createIfAllMandatoryTermsAgreed(
                termsAgreementDto.isServiceTermsAgreed(),
                termsAgreementDto.isPrivacyPolicyAgreed(),
                termsAgreementDto.isDataConsignmentAgreed(),
                termsAgreementDto.isThirdPartyDataSharingAgreed(),
                termsAgreementDto.isMarketingAgreed(),
                null
        );
        saveTermsAgreement(member, agreement);
    }

    private void saveTermsAgreement(Member member, TermsAgreement termsAgreement) {
        if (member == null || termsAgreement == null) throw new IllegalArgumentException("member or termsAgreement cannot be null");
        // 기존의 약관 동의와 달라진 게 없는 경우는 저장하지 않는다
        if (isTermsAgreementUnchanged(member, termsAgreement)) throw new BusinessException(ErrorCode.TERMS_AGREEMENT_NOT_CHANGED);
        termsAgreement.setMember(member);
        termsAgreementRepository.save(termsAgreement);
    }

    private boolean isTermsAgreementUnchanged(Member member, TermsAgreement termsAgreement) {
        TermsAgreement lastAgreement = termsAgreementRepository.findTopByMemberIdOrderByAgreedAtDesc(member.getId());
        return lastAgreement != null && lastAgreement.equals(termsAgreement);
    }

    @Transactional
    public void removeAccount(String memberUuid) {
        // todo 탈퇴할 회원의 Running 전부 비활성화 혹은 삭제 (Soft Delete)
        if(!memberRepository.existsByUuid(memberUuid)) throw new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND, memberUuid);
        memberRepository.deleteByUuid(memberUuid); // id가 아니라 uuid 기준이여도 상관없나? SQLDelete에서는 id = ?으로 주는데
    }

}
