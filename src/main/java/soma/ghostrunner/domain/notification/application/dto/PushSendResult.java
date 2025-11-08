package soma.ghostrunner.domain.notification.application.dto;


public record PushSendResult(
        String pushToken,
        String ticketId,
        SendStatus status,
        String errorMessage
) {
    public enum SendStatus {
        SUCCESS, FAILURE
    }

    public static PushSendResult ofSuccess(String pushToken, String ticketId) {
        return new PushSendResult(pushToken, ticketId, SendStatus.SUCCESS, null);
    }

    public static PushSendResult ofFailure(String pushToken, String errorMessage) {
        return new PushSendResult(pushToken, null, SendStatus.FAILURE, errorMessage);
    }

    public boolean isSuccess() {
        return status == SendStatus.SUCCESS;
    }
    public boolean isFailure() {
        return status == SendStatus.FAILURE;
    }
}
