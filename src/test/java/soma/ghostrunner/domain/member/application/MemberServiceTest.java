package soma.ghostrunner.domain.member.application;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
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
        return Running.of("name", RunningMode.SOLO, null, null, System.currentTimeMillis(), true, false, "telemetry-url", member, null);
    }

    private TermsAgreement createTermsAgreement() {
        return TermsAgreement.createIfAllMandatoryTermsAgreed(true, true, true, true, true, null);
    }

}