package soma.ghostrunner.domain.auth.api.dto.response;


public record AuthenticationResponse(
    String uuid,
    String accessToken,
    String refreshToken
) {}
