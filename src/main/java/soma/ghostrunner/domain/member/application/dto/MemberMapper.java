package soma.ghostrunner.domain.member.application.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import soma.ghostrunner.domain.auth.api.dto.request.SignUpRequest;
import soma.ghostrunner.domain.member.domain.TermsAgreement;

@Mapper(componentModel = "spring")
public interface MemberMapper {

    MemberCreationRequest toMemberCreationRequest(String externalAuthId, SignUpRequest signUpRequest,
                                                  TermsAgreement termsAgreement);

}
