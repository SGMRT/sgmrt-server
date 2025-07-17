package soma.ghostrunner.domain.member.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

class TermsAgreementTest {

    @DisplayName("모든 필수 약관은 동의되어야 한다.")
    @Test
    void verifyAllMandatoryTermsAgreed() {
        // given
        LocalDateTime now = LocalDateTime.now();
        TermsAgreement termsAgreement = TermsAgreement.of(true, true, false,
                true, true, now);

        // when // then
        Assertions.assertThatThrownBy(termsAgreement::verifyAllMandatoryTermsAgreed)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("모든 필수 약관이 동의되어야 함");
     }

}
