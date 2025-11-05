package soma.ghostrunner.global.common.versioning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;

class VersionRangeTest {

    @DisplayName("of()를 호출하면 VersionRange 객체를 생성한다.")
    @Test
    void of() {
        // given
        SemanticVersion version = SemanticVersion.of("1.2.3");
        VersionRange.Operator operator = VersionRange.Operator.GREATER_THAN;
        // when
        VersionRange versionRange = VersionRange.of(version, operator);
        // then
        assertNotNull(versionRange);
        assertThat(versionRange.getVersion()).isEqualTo(version);
        assertThat(versionRange.getOperator()).isEqualTo(operator);
    }

    @DisplayName("parse()를 호출하면 버전 범위 문자열을 파싱하여 VersionRange 객체를 생성한다.")
    @Test
    void parse() {
        // given
        String versionRangeStr1 = "1.2.34^";
        String versionRangeStr2 = "21.0.0v";
        String versionRangeStr3 = "3.10.4";
        // when
        VersionRange range1 = VersionRange.parse(versionRangeStr1);
        VersionRange range2 = VersionRange.parse(versionRangeStr2);
        VersionRange range3 = VersionRange.parse(versionRangeStr3);
        // then
        assertThat(range1.getVersion()).isEqualTo(SemanticVersion.of("1.2.34"));
        assertThat(range1.getOperator()).isEqualTo(VersionRange.Operator.GREATER_THAN_OR_EQUALS);
        assertThat(range2.getVersion()).isEqualTo(SemanticVersion.of("21.0.0"));
        assertThat(range2.getOperator()).isEqualTo(VersionRange.Operator.LESS_THAN_OR_EQUALS);
        assertThat(range3.getVersion()).isEqualTo(SemanticVersion.of("3.10.4"));
        assertThat(range3.getOperator()).isEqualTo(VersionRange.Operator.EQUALS);
    }

    @DisplayName("includes()를 호출하면 주어진 버전이 범위에 포함되는지 여부를 반환한다.")
    @Test
    void includes() {
        // given
        VersionRange Gte_1_2_3 = VersionRange.of(SemanticVersion.of("1.2.3"), VersionRange.Operator.GREATER_THAN_OR_EQUALS);
        VersionRange Lte_2_0_0 = VersionRange.of(SemanticVersion.of("2.0.0"), VersionRange.Operator.LESS_THAN_OR_EQUALS);
        VersionRange Eq_3_1_4 = VersionRange.of(SemanticVersion.of("3.1.4"), VersionRange.Operator.EQUALS);
        SemanticVersion version_1_2_3 = SemanticVersion.of("1.2.3");
        SemanticVersion version_1_5_0 = SemanticVersion.of("1.5.0");
        SemanticVersion version_2_0_0 = SemanticVersion.of("2.0.0");
        SemanticVersion version_2_5_0 = SemanticVersion.of("2.5.0");
        SemanticVersion version_3_1_4 = SemanticVersion.of("3.1.4");
        SemanticVersion version_3_2_0 = SemanticVersion.of("3.2.0");
        // when & then
        assertTrue(Gte_1_2_3.includes(version_1_2_3));
        assertTrue(Gte_1_2_3.includes(version_1_5_0));
        assertFalse(Gte_1_2_3.includes(SemanticVersion.of("1.0.0")));

        assertTrue(Lte_2_0_0.includes(version_2_0_0));
        assertFalse(Lte_2_0_0.includes(version_2_5_0));
        assertTrue(Lte_2_0_0.includes(SemanticVersion.of("1.99.99")));

        assertTrue(Eq_3_1_4.includes(version_3_1_4));
        assertFalse(Eq_3_1_4.includes(version_3_2_0));
    }

}