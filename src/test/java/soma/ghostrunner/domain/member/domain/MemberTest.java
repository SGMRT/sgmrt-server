package soma.ghostrunner.domain.member.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MemberTest {

    @DisplayName("멤버를 of()로 생성 시 필드가 올바르게 저장된다.")
    @Test
    void of() {
        // given // when
        Member member = Member.of("정상수", "프로필 사진 URL");

        // then
        Assertions.assertThat(member.getNickname()).isEqualTo("정상수");
        Assertions.assertThat(member.getProfilePictureUrl()).isEqualTo("프로필 사진 URL");
        Assertions.assertThat(member.getUuid()).isNotNull();
        Assertions.assertThat(member.getBioInfo()).isEqualTo(new MemberBioInfo(null, null, null, null));
        Assertions.assertThat(member.getRole()).isEqualTo(RoleType.USER);
    }


    @DisplayName("멤버를 빌더로 생성 시 필드가 올바르게 저장된다.")
    @Test
    void builder() {
        // given // when
        Member member = Member.builder()
                .nickname("정상수")
                .profilePictureUrl("프로필 사진 URL")
                .build();

        // then
        Assertions.assertThat(member.getNickname()).isEqualTo("정상수");
        Assertions.assertThat(member.getProfilePictureUrl()).isEqualTo("프로필 사진 URL");
        Assertions.assertThat(member.getUuid()).isNotNull();
        Assertions.assertThat(member.getBioInfo()).isEqualTo(new MemberBioInfo(null, null, null, null));
        Assertions.assertThat(member.getRole()).isEqualTo(RoleType.USER);
    }

    @DisplayName("멤버의 역할이 ADMIN인 경우 관리자로 판단한다.")
    @Test
    void isAdminTrue() {
        // given
        Member member = Member.of("정상수", "프로필 사진 URL");
        ReflectionTestUtils.setField(member, "role", RoleType.ADMIN);

        // when // then
        Assertions.assertThat(member.isAdmin()).isTrue();
    }

    @DisplayName("멤버의 역할이 USER인 경우 관리자로 판단하지 않는다.")
    @Test
    void isAdminFalse() {
        // given
        Member member = Member.of("정상수", "프로필 사진 URL");

        // when // then
        Assertions.assertThat(member.isAdmin()).isFalse();
    }

    @DisplayName("페이스메이커 프롬프트로 만들기 위해 멤버 정보를 String으로 변환한다.")
    @Test
    void toStringForPacemakerPrompt() {
        // given
        Member member = Member.of("이복둥", "프로필 사진 URL");
        member.updateBioInfo(Gender.MALE, 28, 60, 170);

        // when // then
        String prompt = member.toStringForPacemakerPrompt(35, 5);
        System.out.println(prompt);
    }

    @DisplayName("유효하지 않은 Condition 값이 입력되면 예외를 발생한다.")
    @Test
    void test() {
        // given
        Member member = Member.of("이복둥", "프로필 사진 URL");
        member.updateBioInfo(Gender.MALE, 28, 60, 170);

        // when // then
        Assertions.assertThatThrownBy(() -> member.toStringForPacemakerPrompt(35, 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 컨디션 값입니다.");
    }

}
