package soma.ghostrunner.domain.notification.application.dto;


public record NotificationSendResult (
        String pushToken,
        String ticketId,
        SendStatus status,
        String errorMessage
) {
    public enum SendStatus {
        SUCCESS, FAILURE
    }

    public static NotificationSendResult ofSuccess(String pushToken, String ticketId) {
        return new NotificationSendResult(pushToken, ticketId, SendStatus.SUCCESS, null);
    }

    public static NotificationSendResult ofFailure(String pushToken, String errorMessage) {
        return new NotificationSendResult(pushToken, null, SendStatus.FAILURE, errorMessage);
    }

    public boolean isSuccess() {
        return status == SendStatus.SUCCESS;
    }
    public boolean isFailure() {
        return status == SendStatus.FAILURE;
    }
}
