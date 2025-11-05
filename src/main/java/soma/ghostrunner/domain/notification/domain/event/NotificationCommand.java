package soma.ghostrunner.domain.notification.domain.event;

import soma.ghostrunner.global.common.versioning.VersionRange;

import java.util.List;
import java.util.Map;

public record NotificationCommand(
        List<Long> memberIds,
        String title,
        String body,
        Map<String, Object> data,
        VersionRange versionRange) {
    public static NotificationCommand of(List<Long> memberIds, String title, String body, Map<String, Object> data) {
        return new NotificationCommand(memberIds, title, body, data, VersionRange.ALL_VERSIONS);
    }

    public static NotificationCommand of(List<Long> memberIds, String title, String body, Map<String, Object> data, VersionRange versionRange) {
        return new NotificationCommand(memberIds, title, body, data, versionRange);
    }
}