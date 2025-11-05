package soma.ghostrunner.domain.notification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.global.common.versioning.SemanticVersion;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;

class DeviceTest {

    @DisplayName("Device 객체를 성공적으로 생성할 수 있다. 기본값이 올바르게 설정된다.")
    @Test
    void createPushToken() {
        // given
        Member member = Member.of("짱구", "profile-url");
        String token = "test-token";

        // when
        Device device = Device.of(member, token);

        // then
        assertThat(device.getToken()).isEqualTo(token);
        assertThat(member.getNickname()).isEqualTo(device.getMember().getNickname());
        assertThat(device.getAppVersion()).isEqualTo(SemanticVersion.of("1.0.0"));
        assertThat(device.getOsName()).isEqualTo("unknown");
        assertThat(device.getOsVersion()).isEqualTo("unknown");
        assertThat(device.getModelName()).isEqualTo("unknown");
        assertThat(device.getUuid()).isNotNull();
        assertThat(device.getDeletedAt()).isNull();
    }

    @DisplayName("빌더로 Device 객체 생성 시 명시하지 않은 필드엔 기본값이 올바르게 설정된다.")
    @Test
    void createPushToken_builder() {
        // given
        Member member = Member.of("흰둥이", "profile-url");
        String token = "test-token";

        try {
            // when - builder로 token만 설정 (private이므로 리플렉션 사용)
            Method m = makeMethodAccessible("builder", Device.class);
            Object builder = m.invoke(null);
            Method tokenMethod = makeMethodAccessible("token", builder.getClass(), String.class);
            tokenMethod.invoke(builder, token);
            Method buildMethod = makeMethodAccessible("build", builder.getClass());
            Device device = (Device) buildMethod.invoke(builder);
            // then
            assertThat(device.getToken()).isEqualTo(token);
            assertThat(device.getMember()).isNull();
            assertThat(device.getAppVersion()).isEqualTo(SemanticVersion.of("1.0.0"));
            assertThat(device.getOsName()).isEqualTo("unknown");
            assertThat(device.getOsVersion()).isEqualTo("unknown");
            assertThat(device.getModelName()).isEqualTo("unknown");
            assertThat(device.getUuid()).isNotNull();
            assertThat(device.getDeletedAt()).isNull();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Method makeMethodAccessible(String methodName, Class<?> clazz, Class<?>... parameterTypes) throws ReflectiveOperationException {
        Method method;
        try {
            method = clazz.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            method = clazz.getMethod(methodName, parameterTypes);
        }
        method.setAccessible(true);
        return method;
    }

}