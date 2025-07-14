package soma.ghostrunner.domain.auth.api.dto.response;

public record SignUpResponse (
    Long uuid,
    String accessToken,
    String refreshToken
) {}
