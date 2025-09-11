package soma.ghostrunner.domain.member.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemberTest {

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
