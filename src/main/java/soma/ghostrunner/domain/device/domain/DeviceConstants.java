package soma.ghostrunner.domain.device.domain;

import soma.ghostrunner.global.common.versioning.SemanticVersion;

public class DeviceConstants {
    public static final String PUSH_TOKEN_PREFIX = "ExponentPushToken[";
    public static final SemanticVersion APP_VERSION_DEFAULT = SemanticVersion.of("1.0.0");
    public static final String OS_NAME_DEFAULT = "unknown";
    public static final String OS_VERSION_DEFAULT = "unknown";
    public static final String OS_MODEL_DEFAULT = "unknown";
}
