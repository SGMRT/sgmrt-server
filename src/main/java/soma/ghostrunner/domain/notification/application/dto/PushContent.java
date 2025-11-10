package soma.ghostrunner.domain.notification.application.dto;

import soma.ghostrunner.global.common.versioning.VersionRange;

import java.util.Map;

public record PushContent(
        String title,
        String body,
        Map<String, Object> data,
        VersionRange versionRange) {
    public static PushContent of(String title, String body, Map<String, Object> data) {
        return new PushContent(title, body, data, VersionRange.ALL_VERSIONS);
    }

    public static PushContent of(String title, String body, Map<String, Object> data, VersionRange versionRange) {
        return new PushContent(title, body, data, versionRange);
    }
}