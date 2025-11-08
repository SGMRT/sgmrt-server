package soma.ghostrunner.domain.notification.application.dto;

import soma.ghostrunner.global.common.versioning.VersionRange;

import java.util.List;
import java.util.Map;

public record PushCommand(
        List<Long> memberIds,
        String title,
        String body,
        Map<String, Object> data,
        VersionRange versionRange) {
    public static PushCommand of(List<Long> memberIds, String title, String body, Map<String, Object> data) {
        return new PushCommand(memberIds, title, body, data, VersionRange.ALL_VERSIONS);
    }

    public static PushCommand of(List<Long> memberIds, String title, String body, Map<String, Object> data, VersionRange versionRange) {
        return new PushCommand(memberIds, title, body, data, versionRange);
    }
}