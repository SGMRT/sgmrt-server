package soma.ghostrunner.domain.auth.api.dto.response;


public record SignInResponse (
    String uuid,
    String accessToken,
    String refreshToken
) {}
