package soma.ghostrunner.domain.auth.api.dto;

import org.mapstruct.Mapper;
import soma.ghostrunner.domain.auth.api.dto.response.AuthenticationResponse;
import soma.ghostrunner.domain.auth.application.dto.JwtTokens;

@Mapper(componentModel = "spring")
public interface AuthMapper {

    AuthenticationResponse toAuthenticationResponse(String uuid, String accessToken, String refreshToken);

    default AuthenticationResponse toAuthenticationResponse(String uuid, JwtTokens tokens) {
        return toAuthenticationResponse(uuid, tokens.accessToken(), tokens.refreshToken());
    }

}
