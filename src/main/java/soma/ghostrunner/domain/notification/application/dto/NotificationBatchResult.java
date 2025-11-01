package soma.ghostrunner.domain.notification.application.dto;

import java.util.List;

public record NotificationBatchResult(
        int totalCount,
        int successCount,
        int failureCount,

        List<String> successPushTokens,
        List<String> failurePushTokens
) {
    public static NotificationBatchResult ofEmpty() {
        return new NotificationBatchResult(0, 0, 0, List.of(), List.of());
    }

}