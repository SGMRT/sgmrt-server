package soma.ghostrunner.domain.member.application.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import soma.ghostrunner.domain.auth.api.dto.request.SignUpRequest;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.api.dto.response.MemberResponse;
import soma.ghostrunner.domain.member.domain.TermsAgreement;

@Mapper(componentModel = "spring")
public interface MemberMapper {

    MemberCreationRequest toMemberCreationRequest(String externalAuthId, SignUpRequest signUpRequest,
                                                  TermsAgreement termsAgreement);

    @Mapping(source = "bioInfo.gender", target = "gender")
    @Mapping(source = "bioInfo.weight", target = "weight")
    @Mapping(source = "bioInfo.height", target = "height")
    MemberResponse toMemberResponse(Member member);

}
