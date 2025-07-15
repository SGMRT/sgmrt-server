package soma.ghostrunner.domain.auth.api.dto;

import org.mapstruct.Mapper;
import soma.ghostrunner.domain.auth.api.dto.response.SignInResponse;
import soma.ghostrunner.domain.auth.api.dto.response.SignUpResponse;

@Mapper(componentModel = "spring")
public interface AuthMapper {
    SignInResponse toSignInResponse(String uuid, String accessToken, String refreshToken);

    SignUpResponse toSignUpResponse(String uuid, String accessToken, String refreshToken);
}
