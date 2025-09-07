package soma.ghostrunner.domain.notification.application.dto;


public record NotificationSendResult (
    Long notificationId,
    String ticketId,
    SendStatus status,
    String errorMessage
) {
    public enum SendStatus {
        SUCCESS, FAILURE
    }

    public static NotificationSendResult ofSuccess(Long notificationId, String ticketId) {
        return new NotificationSendResult(notificationId, ticketId, SendStatus.SUCCESS, null);
    }

    public static NotificationSendResult ofFailure(Long notificationId, String errorMessage) {
        return new NotificationSendResult(notificationId, null, SendStatus.FAILURE, errorMessage);
    }

    public boolean isSuccess() {
        return status == SendStatus.SUCCESS;
    }
    public boolean isFailure() {
        return status == SendStatus.FAILURE;
    }
}
