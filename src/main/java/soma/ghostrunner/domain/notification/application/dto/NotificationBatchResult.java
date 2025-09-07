package soma.ghostrunner.domain.notification.application.dto;

public record NotificationBatchResult(
        int totalCount,
        int successCount,
        int failureCount
) {}