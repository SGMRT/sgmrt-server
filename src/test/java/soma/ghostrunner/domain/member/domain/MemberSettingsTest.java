package soma.ghostrunner.domain.member.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberSettingsTest {

    @Test
    @DisplayName("MemberSettings 생성 시 메서드에 명시한 필드대로 생성된다")
    void testCreation() {
        // given
        Member member1 = Member.of("testName1", "https://testUrl.com");
        Member member2 = Member.of("testName2", "https://testUrl2.com");

        // when
        MemberSettings settings1 = MemberSettings.of(member1, true, true);
        MemberSettings settings2 = MemberSettings.of(member2, false, false);

        // then
        Assertions.assertThat(settings1.isPushAlarmEnabled()).isTrue();
        Assertions.assertThat(settings1.isVibrationEnabled()).isTrue();
        Assertions.assertThat(settings2.isPushAlarmEnabled()).isFalse();
        Assertions.assertThat(settings2.isVibrationEnabled()).isFalse();
    }

    @Test
    @DisplayName("MemberSettings 생성 시 필드를 명시하지 않으면 기본값이 적용된다.")
    void testCreationDefaultValue() {
        // given
        Member member = Member.of("testName", "https://testUrl.com");
        final boolean PUSH_ALARM_DEFAULT = true;
        final boolean VIBRATION_DEFAULT = true;

        // when
        MemberSettings settings = MemberSettings.of(member);

        // then
        Assertions.assertThat(settings.isPushAlarmEnabled()).isEqualTo(PUSH_ALARM_DEFAULT);
        Assertions.assertThat(settings.isVibrationEnabled()).isEqualTo(VIBRATION_DEFAULT);
    }

}