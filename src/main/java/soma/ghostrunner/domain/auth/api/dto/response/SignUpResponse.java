package soma.ghostrunner.domain.auth.api.dto.response;

public record SignUpResponse (
    String uuid,
    String accessToken,
    String refreshToken
) {}
