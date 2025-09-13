package soma.ghostrunner.domain.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.clients.aws.presign.S3PresignUrlClient;
import soma.ghostrunner.domain.member.api.dto.TermsAgreementDto;
import soma.ghostrunner.domain.member.api.dto.request.MemberSettingsUpdateRequest;
import soma.ghostrunner.domain.member.api.dto.request.MemberUpdateRequest;
import soma.ghostrunner.domain.member.api.dto.response.MemberResponse;
import soma.ghostrunner.domain.member.infra.dao.*;
import soma.ghostrunner.domain.member.domain.*;
import soma.ghostrunner.domain.member.domain.Gender;
import soma.ghostrunner.domain.member.exception.InvalidMemberException;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.member.application.dto.MemberCreationRequest;
import soma.ghostrunner.domain.member.domain.MemberAuthInfo;
import soma.ghostrunner.domain.member.domain.TermsAgreement;
import soma.ghostrunner.global.error.ErrorCode;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static soma.ghostrunner.global.error.ErrorCode.MEMBER_ALREADY_EXISTED;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final TermsAgreementRepository termsAgreementRepository;
    private final MemberAuthInfoRepository memberAuthInfoRepository;
    private final MemberSettingsRepository memberSettingsRepository;
    private final MemberVdotRepository memberVdotRepository;

    @Transactional(readOnly = true)
    public Member findMemberByUuid(String uuid) {
        return memberRepository.findByUuid(uuid)
                .orElseThrow(() -> new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND, "cannot find member uuid: " + uuid));
    }

    public String findUuidByAuthUid(String authUid) {
        return memberAuthInfoRepository.findMemberUuidByExternalAuthUid(authUid)
                .orElseThrow(() -> new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND));
    }

    public void verifyAuthUidUnique(String authUid) {
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
        if(request.getUpdateAttrs() == null || request.getUpdateAttrs().isEmpty()) {
            return;
        }

        Member member = findMemberByUuid(uuid);
        Gender gender = member.getBioInfo().getGender();
        Integer weight = member.getBioInfo().getWeight();
        Integer height = member.getBioInfo().getHeight();
        Integer age = member.getBioInfo().getAge();
        for(MemberUpdateRequest.UpdatedAttr attr : request.getUpdateAttrs()) {
            switch(attr) {
                case NICKNAME:
                    verifyNickname(request.getNickname());
                    member.updateNickname(request.getNickname());
                    break;
                case GENDER:
                    gender = request.getGender();
                    break;
                case AGE:
                    age = request.getAge();
                    break;
                case HEIGHT:
                    verifyHeight(request.getHeight());
                    height = request.getHeight();
                    break;
                case WEIGHT:
                    verifyWeight(request.getHeight());
                    weight = request.getWeight();
                    break;
                case PROFILE_IMAGE_URL:
                    verifyImageUrl(request.getProfileImageUrl());
                    member.updateProfilePictureUrl(request.getProfileImageUrl());
                    break;
            }
        }

        member.updateBioInfo(gender, age, weight, height);
    }

    private void verifyImageUrl(String profileImageUrl) {
        // image는 null 입력 허용
        if (profileImageUrl == null) return;
        try {
            URL url = new URL(profileImageUrl);
            Set<String> allowedExtensions = S3PresignUrlClient.PROFILE_IMAGE_ALLOWED_EXTENSIONS;
            boolean isExtensionValid = allowedExtensions.stream().anyMatch(profileImageUrl::endsWith);
            if(!isExtensionValid) {
                throw new IllegalArgumentException("unsupported image url extension: " + profileImageUrl);
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("invalid url: " + profileImageUrl);
        }
    }

    private void verifyNickname(String nickname) {
        if(nickname == null) throw new IllegalArgumentException("nickname cannot be null");
        checkNicknameDuplication(nickname);
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
    public Member createAndSaveMember(MemberCreationRequest creationRequest) {
        Member member = Member.builder()
                .nickname(creationRequest.getNickname())
                .bioInfo(new MemberBioInfo(creationRequest.getGender(),
                                           creationRequest.getAge(),
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
        // MemberAuthInfo, TermsAgreement, MemberSettings 저장
        if (member == null || externalAuthId == null || termsAgreement == null) throw new IllegalArgumentException();

        MemberAuthInfo memberAuthInfo = MemberAuthInfo.of(member, externalAuthId);
        memberAuthInfoRepository.save(memberAuthInfo);

        recordTermsAgreement(member, termsAgreement, LocalDateTime.now());

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
        Member member = findMemberByUuid(memberUuid);
        MemberSettings currentSettings = memberSettingsRepository.findByMember_Uuid(memberUuid)
                .orElse(MemberSettings.of(member));

        currentSettings.updateSettings(request.getPushAlarmEnabled(),
                request.getVibrationEnabled(), request.getVoiceGuidanceEnabled());
        memberSettingsRepository.save(currentSettings);
    }

    @Transactional
    public void recordTermsAgreement(String memberUuid, TermsAgreementDto termsAgreementDto, LocalDateTime agreedAt) {
        Member member = findMemberByUuid(memberUuid);
        if(termsAgreementDto == null) throw new IllegalArgumentException("termsAgreement cannot be null");
        TermsAgreement agreement = TermsAgreement.createIfAllMandatoryTermsAgreed(
                termsAgreementDto.isServiceTermsAgreed(),
                termsAgreementDto.isPrivacyPolicyAgreed(), termsAgreementDto.isPersonalInformationUsageConsentAgreed(),
                null
        );
        recordTermsAgreement(member, agreement, agreedAt);
    }

    private void recordTermsAgreement(Member member, TermsAgreement newAgreement, LocalDateTime agreedAt) {
        if (member == null || newAgreement == null) throw new IllegalArgumentException("member or termsAgreement cannot be null");
        Optional<TermsAgreement> lastAgreement = termsAgreementRepository.findTopByMemberIdOrderByAgreedAtDesc(member.getId());

        // 기존의 약관 동의와 달라진 게 없는 경우 agreedAt만 갱신한다
        if (lastAgreement.isPresent() && lastAgreement.get().equals(newAgreement)) {
            lastAgreement.get().renewAgreedAt(agreedAt); // 더티 체킹
            return;
        }

        newAgreement.setMember(member);
        termsAgreementRepository.save(newAgreement);
    }

    @Transactional
    public void removeAccount(String memberUuid) {
        if(!memberRepository.existsByUuid(memberUuid)) throw new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND, memberUuid);
        memberRepository.deleteByUuid(memberUuid);
    }

    @Transactional(readOnly = true)
    public MemberVdot findMemberVdot(Member member) {
        return memberVdotRepository.findByMemberId(member.getId())
                .orElseThrow(() -> new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND, "cannot find vdot, memberUuid: " + member.getUuid()));
    }

}
