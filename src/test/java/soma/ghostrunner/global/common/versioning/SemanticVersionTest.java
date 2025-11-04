package soma.ghostrunner.global.common.versioning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SemanticVersionTest {

    @DisplayName("of()를 호출하면 버전 문자열을 파싱하여 객체를 생성한다.")
    @Test
    void of() {
        // given
        String versionStr = "1.2.3";
        // when
        SemanticVersion version = SemanticVersion.of(versionStr);
        // then
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getPatch());
    }

    @DisplayName("of()를 호출할 때 잘못된 형식의 버전 문자열을 전달하면 IllegalArgumentException이 발생한다.")
    @ParameterizedTest
    @MethodSource("invalidVersionStrings")
    void of_invalidFormat(String versionStr) {
        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            SemanticVersion.of(versionStr);
        });
        assertTrue(exception.getMessage().startsWith("잘못된 버전 형식"));

    }

    private static Stream<Arguments> invalidVersionStrings() {
        return Stream.of(
                Arguments.of("1.2"),            // 부분 버전 누락
                Arguments.of("1.2.a"),          // 숫자 X
                Arguments.of("-1.2.3"),         // 음수 포함
                Arguments.of("1.-2.3"),         // 음수 포함
                Arguments.of("1.2.-3"),         // 음수 포함
                Arguments.of("1..3"),           // 중간 버전 누락
                Arguments.of("1.2.3.4"),        // 너무 많은 온점
                Arguments.of("abc.def.ghi"),    // 숫자 X
                Arguments.of("12gs\n3atsa das") // 이건 좀
        );
    }

    @DisplayName("compareTo()를 호출하면 버전 객체 간의 대소 관계를 올바르게 비교한다.")
    @Test
    void compareTo() {
        // given
        // 아래로 갈 수록 버전이 커짐
        SemanticVersion v1 = SemanticVersion.of("1.2.3");
        SemanticVersion v2 = SemanticVersion.of("1.2.3");
        SemanticVersion v3 = SemanticVersion.of("1.2.4");
        SemanticVersion v4 = SemanticVersion.of("1.3.0");
        SemanticVersion v5 = SemanticVersion.of("2.0.0");

        // when & then
        assertEquals(0, v1.compareTo(v2));
        assertTrue(v1.compareTo(v3) < 0);
        assertTrue(v3.compareTo(v1) > 0);
        assertTrue(v3.compareTo(v4) < 0);
        assertTrue(v4.compareTo(v5) < 0);
    }

    @DisplayName("toString()을 호출하면 버전 문자열을 올바르게 반환한다.")
    @Test
    void testToString() {
        // given
        SemanticVersion version = SemanticVersion.of("1.23.45");
        // when
        String versionStr = version.toString();
        // then
        assertEquals("1.23.45", versionStr);
    }

    @DisplayName("equals()가 올바르게 동작한다.")
    @Test
    void testEquals() {
        // given
        SemanticVersion v1a = SemanticVersion.of("1.2.3");
        SemanticVersion v1b = SemanticVersion.of("1.2.3");
        SemanticVersion v2 = SemanticVersion.of("1.2.4");
        // when & then
        assertEquals(v1a, v1b);
        assertNotEquals(v1a, v2);
    }

    @DisplayName("hashCode()가 올바르게 동작한다.")
    @Test
    void testHashCode() {
        // given
        SemanticVersion v1a = SemanticVersion.of("1.2.3");
        SemanticVersion v1b = SemanticVersion.of("1.2.3");
        SemanticVersion v2 = SemanticVersion.of("1.2.4");
        // when & then
        assertEquals(v1a.hashCode(), v1b.hashCode());
        assertNotEquals(v1a.hashCode(), v2.hashCode());
    }

}