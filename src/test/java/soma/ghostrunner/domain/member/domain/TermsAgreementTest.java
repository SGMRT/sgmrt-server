package soma.ghostrunner.domain.member.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

class TermsAgreementTest {

    @DisplayName("약관 동의를 생성한다.")
    @Test
    void createIfAllMandatoryTermsAgreed() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when
        TermsAgreement termsAgreement = TermsAgreement.createIfAllMandatoryTermsAgreed(true,
                true, true, true, true, now);

        // when // then
        Assertions.assertThat(termsAgreement.getAgreedAt()).isEqualTo(now);
        Assertions.assertThat(termsAgreement.isServiceTermsAgreed()).isTrue();
        Assertions.assertThat(termsAgreement.isPrivacyPolicyAgreed()).isTrue();
    }

    @DisplayName("모든 필수 약관은 동의되어야 한다.")
    @Test
    void allMandatoryTermsMustBeAgreed() {
        // when // then
        Assertions.assertThatThrownBy(() -> TermsAgreement.createIfAllMandatoryTermsAgreed(true,
                        true, false, true, true, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("모든 필수 약관이 동의되어야 함");
    }

}
