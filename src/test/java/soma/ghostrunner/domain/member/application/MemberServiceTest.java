package soma.ghostrunner.domain.member.application;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseDataUrls;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.member.api.dto.TermsAgreementDto;
import soma.ghostrunner.domain.member.api.dto.request.MemberUpdateRequest;
import soma.ghostrunner.domain.member.application.dto.MemberCreationRequest;
import soma.ghostrunner.domain.member.domain.*;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.member.infra.dao.*;
import soma.ghostrunner.domain.member.exception.InvalidMemberException;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;


class MemberServiceTest extends IntegrationTestSupport {

    @Autowired MemberService memberService;
    @Autowired MemberRepository memberRepository;
    @Autowired MemberSettingsRepository memberSettingsRepository;
    @Autowired MemberAuthInfoRepository memberAuthInfoRepository;
    @Autowired TermsAgreementRepository termsAgreementRepository;
    @Autowired RunningRepository runningRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberVdotRepository memberVdotRepository;

    @DisplayName("회원가입 성공 시 입력한 정보대로 회원을 저장한다.")
    @Test
    void signUp() {
        // given
        MemberCreationRequest request = MemberCreationRequest.builder()
                .nickname("장원영")
                .externalAuthId("testAuthId123")
                .profileImageUrl("https://example.com/profile.jpg")
                .gender(Gender.FEMALE)
                .age(25)
                .weight(70)
                .height(175)
                .termsAgreement(createTermsAgreement())
                .build();

        // when
        Member member = memberService.createAndSaveMember(request);

        // then
        assertNotNull(member);
        assertNotNull(member.getId());
        assertNotNull(member.getUuid());
        assertThat(member.getNickname()).isEqualTo("장원영");
        assertThat(member.getBioInfo().getGender()).isEqualTo(Gender.FEMALE);
        assertThat(member.getBioInfo().getAge()).isEqualTo(25);
        assertThat(member.getBioInfo().getWeight()).isEqualTo(70);
        assertThat(member.getBioInfo().getHeight()).isEqualTo(175);
    }

    @DisplayName("회원가입 시 인증정보, 설정, 약관동의 엔티티가 함께 저장된다.")
    @Test
    void signUp_withAssociatedEntities() {
        // given
        TermsAgreement termsAgreement = createTermsAgreement();
        MemberCreationRequest request = MemberCreationRequest.builder()
                .nickname("이상혁")
                .externalAuthId("testAuthId123")
                .profileImageUrl("https://example.com/profile.jpg")
                .gender(Gender.MALE)
                .age(25)
                .weight(70)
                .height(175)
                .termsAgreement(termsAgreement)
                .build();

        // when
        Member member = memberService.createAndSaveMember(request);

        // then
        assertNotNull(member);

        // - MemberAuthInfo 검증
        MemberAuthInfo memberAuthInfo = memberAuthInfoRepository.findByMemberId(member.getId()).orElseThrow();
        assertThat(memberAuthInfo.getExternalAuthUid()).isEqualTo("testAuthId123");

        // - MemberSettings 검증
        MemberSettings memberSettings = memberSettingsRepository.findByMember_Uuid(member.getUuid()).orElseThrow();
        assertThat(memberSettings.isPushAlarmEnabled()).isTrue();
        assertThat(memberSettings.isVibrationEnabled()).isTrue();
        assertThat(memberSettings.isVoiceGuidanceEnabled()).isTrue();

        // - TermsAgreement 검증
        TermsAgreement savedTermsAgreement = termsAgreementRepository.findByMemberId(member.getId()).orElseThrow();
        assertThat(savedTermsAgreement).isEqualTo(termsAgreement);
    }

    @DisplayName("중복 닉네임으로 회원가입 시 예외가 발생한다.")
    @Test
    void signUp_withDuplicateNickname() {
        // given
        MemberCreationRequest request1 = MemberCreationRequest.builder()
                .nickname("duplicate")
                .externalAuthId("testAuthId1")
                .age(25)
                .gender(Gender.MALE)
                .termsAgreement(createTermsAgreement())
                .build();

        MemberCreationRequest request2 = MemberCreationRequest.builder()
                .nickname("duplicate")
                .externalAuthId("testAuthId2")
                .age(25)
                .gender(Gender.MALE)
                .termsAgreement(createTermsAgreement())
                .build();

        // when
        memberService.createAndSaveMember(request1);

        // then
        assertThatThrownBy(() -> memberService.createAndSaveMember(request2))
                .isInstanceOf(InvalidMemberException.class)
                .hasMessageContaining("존재하는 닉네임");
    }

    @DisplayName("회원 정보 수정에 성공하면 변경한 내용이 반영된다.")
    @Test
    void updateMember() {
        // given
        Member member = createAndSaveMember("윈터");
        String uuid = member.getUuid();
        memberRepository.save(member);
        MemberUpdateRequest request = new MemberUpdateRequest(
                "카리나", Gender.FEMALE, 175, 70, 26, "https://testUrl.com/picture.jpg",
                Set.of(MemberUpdateRequest.UpdatedAttr.NICKNAME, MemberUpdateRequest.UpdatedAttr.GENDER,
                MemberUpdateRequest.UpdatedAttr.AGE, MemberUpdateRequest.UpdatedAttr.HEIGHT,
                MemberUpdateRequest.UpdatedAttr.WEIGHT, MemberUpdateRequest.UpdatedAttr.PROFILE_IMAGE_URL));

        // when
        memberService.updateMember(uuid, request);

        // then
        Member updatedMember = memberRepository.findByUuid(uuid).orElseThrow();
        assertThat(updatedMember.getNickname()).isEqualTo("카리나");
        assertThat(updatedMember.getBioInfo().getGender()).isEqualTo(Gender.FEMALE);
        assertThat(updatedMember.getBioInfo().getHeight()).isEqualTo(175);
        assertThat(updatedMember.getBioInfo().getWeight()).isEqualTo(70);
        assertThat(updatedMember.getProfilePictureUrl()).isEqualTo("https://testUrl.com/picture.jpg");
    }

    @DisplayName("회원 정보 수정 시 updateAttr에 명시한 필드만 수정된다.")
    @Test
    void updateMember_specifiedAttrOnly() {
        // given
        Member member = createAndSaveMember("아이유");
        String uuid = member.getUuid();
        memberRepository.save(member);
        // 변경할 필드에 nickname만 명시한다
        MemberUpdateRequest request = MemberUpdateRequest.builder()
                .nickname("이상혁")
                .height(200)
                .updateAttrs(Set.of(MemberUpdateRequest.UpdatedAttr.NICKNAME))
                .build();

        // when
        memberService.updateMember(uuid, request);

        // then
        // - nickname만 변경되고 나머지는 그대로여야 함
        Member updatedMember = memberRepository.findByUuid(uuid).orElseThrow();
        assertThat(updatedMember.getNickname()).isEqualTo("이상혁");
        assertThat(updatedMember.getBioInfo().getGender()).isEqualTo(member.getBioInfo().getGender());
        assertThat(updatedMember.getBioInfo().getHeight()).isEqualTo(member.getBioInfo().getHeight());
        assertThat(updatedMember.getBioInfo().getWeight()).isEqualTo(member.getBioInfo().getWeight());
        assertThat(updatedMember.getProfilePictureUrl()).isEqualTo(member.getProfilePictureUrl());
    }

    @DisplayName("회원 정보 수정 시 닉네임이 중복이면 예외가 발생한다.")
    @Test
    void updateMember_duplicateNickname() {
        // given
        Member member1 = createAndSaveMember("유나");
        memberRepository.save(member1);

        Member member2 = createAndSaveMember("유나아님");
        memberRepository.save(member2);
        String uuid = member2.getUuid();
        MemberUpdateRequest request = MemberUpdateRequest.builder()
                .nickname("유나")
                .updateAttrs(Set.of(MemberUpdateRequest.UpdatedAttr.NICKNAME))
                .build();

        // when & then
        assertThatThrownBy(() -> memberService.updateMember(uuid, request))
                .isInstanceOf(InvalidMemberException.class)
                .hasMessageContaining("존재하는 닉네임");
    }

    @DisplayName("약관 동의 시 약관 동의 정보가 저장된다.")
    @Test
    void recordTermsAgreement() {
        // given
        Member member = createAndSaveMember("윈터");
        String uuid = member.getUuid();
        memberRepository.save(member);

        TermsAgreementDto newAgreementDto = new TermsAgreementDto(
                true, true, true, null
        );

        // when
        memberService.recordTermsAgreement(uuid, newAgreementDto, null);

        // then
        TermsAgreement savedAgreement = termsAgreementRepository.findByMemberId(member.getId()).orElseThrow();
        assertThat(savedAgreement.isServiceTermsAgreed()).isTrue();
        assertThat(savedAgreement.isPrivacyPolicyAgreed()).isTrue();
        assertThat(savedAgreement.isPersonalInformationUsageConsentAgreed()).isTrue();
        assertThat(savedAgreement.getAgreedAt()).isNotNull();
    }

    @DisplayName("약관 동의 항목이 기존 동의 항목과 동일한 경우 agreedAt만 갱신된다.")
    @Test
    void recordTermsAgreement_identical() {
        // given
        Member member = createAndSaveMember("윈터");
        String uuid = member.getUuid();
        memberRepository.save(member);

        TermsAgreement existingAgreement = createTermsAgreement();
        existingAgreement.setMember(member);
        termsAgreementRepository.save(existingAgreement);

        TermsAgreementDto newAgreementDto = new TermsAgreementDto(true, true, true, null);

        // when
        LocalDateTime agreedAtBefore = existingAgreement.getAgreedAt();
        memberService.recordTermsAgreement(uuid, newAgreementDto, agreedAtBefore.plusDays(1));

        // then
        TermsAgreement savedAgreement = termsAgreementRepository.findByMemberId(member.getId()).orElseThrow();
        assertThat(savedAgreement.getAgreedAt()).isEqualTo(agreedAtBefore.plusDays(1));
        assertThat(savedAgreement.isServiceTermsAgreed()).isEqualTo(existingAgreement.isServiceTermsAgreed());
        assertThat(savedAgreement.isPrivacyPolicyAgreed()).isEqualTo(existingAgreement.isPrivacyPolicyAgreed());
        assertThat(savedAgreement.isPersonalInformationUsageConsentAgreed()).isEqualTo(existingAgreement.isPersonalInformationUsageConsentAgreed());
    }


    @DisplayName("회원 탈퇴 성공 시 엔티티가 삭제되어 조회할 수 없다.")
    @Test
    void removeAccount() {
        // given
        Member member = createAndSaveMember("카리나");
        String uuid = member.getUuid();
        memberRepository.save(member);

        // when
        memberService.removeAccount(uuid);

        // then
        assertThat(memberRepository.findByUuid(uuid)).isNotPresent();
    }

    @DisplayName("회원 탈퇴 성공 시 연관된 Running 엔티티가 함께 삭제된다.")
    @Test
    void removeAccount_cascadeRuns() {
        // given
        Member member = createAndSaveMember("카리나");
        String uuid = member.getUuid();
        memberRepository.save(member);

        Running running1 = createRunning("run 1", member);
        Running running2 = createRunning("run 2", member);
        runningRepository.saveAll(List.of(running1, running2));

        // when
        memberService.removeAccount(uuid);

        // then
        assertThat(runningRepository.findAll().size()).isEqualTo(0);
    }

    @DisplayName("회원 탈퇴를 하더라도 연관된 Course 엔티티는 그대로 조회 가능하다.")
    @Test
    void removeAccount_cascadeCourses() {
        // given
        Member member = createAndSaveMember("카리나");
        var uuid = member.getUuid();
        memberRepository.save(member);

        var course1 = createCourse(member);
        var course2 = createCourse(member);
        courseRepository.saveAll(List.of(course1, course2));

        // when
        memberService.removeAccount(uuid);

        // then
        List<Course> courses = courseRepository.findAll();
        assertThat(courses.size()).isEqualTo(2);
        assertThat(courses.get(0).getId()).isEqualTo(course1.getId());
        assertThat(courses.get(1).getId()).isEqualTo(course2.getId());
    }

    @DisplayName("멤버의 VDOT를 조회한다.")
    @Test
    void findMemberVdot() {
        // given
        Member member = createAndSaveMember("카리나");
        memberRepository.save(member);

        MemberVdot memberVdot = new MemberVdot(25, member);
        memberVdotRepository.save(memberVdot);

        // when
        MemberVdot savedMemberVdot = memberService.findMemberVdot(member);

        // then
        Assertions.assertThat(savedMemberVdot.getVdot()).isEqualTo(memberVdot.getVdot());
    }

    @DisplayName("멤버의 VDOT가 없다면 예외를 발생한다.")
    @Test
    void findNoneMemberVdot() {
        // given
        Member member = createAndSaveMember("카리나");
        memberRepository.save(member);

        // when // then
        Assertions.assertThatThrownBy(() -> memberService.findMemberVdot(member))
                .isInstanceOf(MemberNotFoundException.class)
                .hasMessage("cannot find vdot, memberUuid: " + member.getUuid());
    }

    // --- Helper methods ---

    private Member createAndSaveMember(String name) {
        return Member.of(name, "test-url");
    }

    private Running createRunning(String name, Member member) {
        return Running.of("name", RunningMode.SOLO, null, createRunningRecord(),
                System.currentTimeMillis(), true, false,
                "telemetry-url", "telemetry-url", "telemetry-url",
                member, null);
    }

    private RunningRecord createRunningRecord() {
        return RunningRecord.of(
                5.2, 40.0, 30.0, -20.0,
                6.1, 3423.2, 302.2, 120L, 56, 100, 120);
    }

    private TermsAgreement createTermsAgreement() {
        return TermsAgreement.createIfAllMandatoryTermsAgreed(true, true, true, null);
    }

    private Course createCourse(Member member) {
        CourseProfile profile = CourseProfile.of(5.0, 10.0, 100.0, 50.0);
        Coordinate coordinate = Coordinate.of(37d, 129d);
        CourseDataUrls urls = CourseDataUrls.of("route.url", "checkpoint.url", "thumbnail.url");

        return Course.of(member, "테스트 코스", profile, coordinate, CourseSource.USER, true, urls);
    }

}
