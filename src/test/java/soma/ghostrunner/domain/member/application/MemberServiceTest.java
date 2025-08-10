package soma.ghostrunner.domain.member.application;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.member.api.dto.request.MemberUpdateRequest;
import soma.ghostrunner.domain.member.application.dto.MemberCreationRequest;
import soma.ghostrunner.domain.member.dao.MemberRepository;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.domain.TermsAgreement;
import soma.ghostrunner.domain.member.enums.Gender;
import soma.ghostrunner.domain.member.exception.InvalidMemberException;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;


class MemberServiceTest extends IntegrationTestSupport {

    @Autowired
    MemberService memberService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    RunningRepository runningRepository;

    @Autowired
    CourseRepository courseRepository;

    @DisplayName("회원가입이 성공하면 관련 엔티티도 성공적으로 저장된다.")
    @Test
    void signUp_success() {
        // given
        MemberCreationRequest request = MemberCreationRequest.builder()
                .nickname("testNickname")
                .externalAuthId("testAuthId123")
                .profileImageUrl("http://example.com/profile.jpg")
                .gender(Gender.MALE)
                .weight(70)
                .height(175)
                .termsAgreement(createTermsAgreement())
                .build();

        // when
        Member member = memberService.createMember(request);

        // then
        Assertions.assertNotNull(member);
        Assertions.assertNotNull(member.getId());
        Assertions.assertEquals("testNickname", member.getNickname());
    }

    @DisplayName("중복 닉네임으로 회원가입 시 예외가 발생한다.")
    @Test
    void signUpWithDuplicateNickname_fail() {
        // given
        MemberCreationRequest request1 = MemberCreationRequest.builder()
                .nickname("duplicate")
                .externalAuthId("testAuthId1")
                .gender(Gender.MALE)
                .termsAgreement(createTermsAgreement())
                .build();

        MemberCreationRequest request2 = MemberCreationRequest.builder()
                .nickname("duplicate")
                .externalAuthId("testAuthId2")
                .gender(Gender.MALE)
                .termsAgreement(createTermsAgreement())
                .build();

        // when
        memberService.createMember(request1);

        // then
        assertThatThrownBy(() -> memberService.createMember(request2))
                .isInstanceOf(InvalidMemberException.class)
                .hasMessageContaining("존재하는 닉네임");
    }

    @DisplayName("회원 정보 수정에 성공하면 해당 내용이 반영된다")
    @Test
    void updateMember_success() {
        // given
        Member member = createMember("윈터");
        String uuid = member.getUuid();
        memberRepository.save(member);
        MemberUpdateRequest request = new MemberUpdateRequest(
                "카리나", Gender.FEMALE, 175, 70, "https://testUrl.com/picture.jpg",
                Set.of(MemberUpdateRequest.UpdatedAttr.NICKNAME, MemberUpdateRequest.UpdatedAttr.GENDER,
                MemberUpdateRequest.UpdatedAttr.HEIGHT, MemberUpdateRequest.UpdatedAttr.WEIGHT,
                MemberUpdateRequest.UpdatedAttr.PROFILE_IMAGE_URL));

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

    @DisplayName("회원 정보 수정 시 updateAttr에 명시한 필드만 수정된다")
    @Test
    void updateMember_specifiedAttrOnly() {
        // given
        Member member = createMember("아이유");
        String uuid = member.getUuid();
        memberRepository.save(member);
        MemberUpdateRequest request = new MemberUpdateRequest(
                "이상혁", null, 200, null, null,
                Set.of(MemberUpdateRequest.UpdatedAttr.NICKNAME));

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

    @DisplayName("회원 정보 수정 시 중복 닉네임으로 변경하면 예외가 발생한다")
    @Test
    void updateMember_duplicateNickname() {
        // given
        Member member1 = createMember("유나");
        memberRepository.save(member1);

        Member member2 = createMember("유나아님");
        memberRepository.save(member2);
        String uuid = member2.getUuid();
        MemberUpdateRequest request = new MemberUpdateRequest("유나", null, null, null, null,
                Set.of(MemberUpdateRequest.UpdatedAttr.NICKNAME));

        // when & then
        assertThatThrownBy(() -> memberService.updateMember(uuid, request))
                .isInstanceOf(InvalidMemberException.class)
                .hasMessageContaining("존재하는 닉네임");
    }


    @DisplayName("회원 탈퇴 성공 시 엔티티가 삭제되어 조회할 수 없다.")
    @Test
    void removeAccount_success() {
        // given
        Member member = createMember("카리나");
        String uuid = member.getUuid();
        memberRepository.save(member);

        // when
        memberService.removeAccount(uuid);

        // then
        assertThat(memberRepository.findByUuid(uuid)).isNotPresent();
    }


    @DisplayName("회원 탈퇴 성공 시 연관된 Running 엔티티가 함께 삭제된다.")
    @Test
    void removeAccount_cascade() {
        // given
        Member member = createMember("카리나");
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

    private Member createMember(String name) {
        return Member.of(name, "test-url");
    }

    private Running createRunning(String name, Member member) {
        return Running.of("name", RunningMode.SOLO, null, null,
                System.currentTimeMillis(), true, false,
                "telemetry-url", "telemetry-url", "telemetry-url",
                member, null);
    }

    private TermsAgreement createTermsAgreement() {
        return TermsAgreement.createIfAllMandatoryTermsAgreed(true, true, true, true, true, null);
    }

}
