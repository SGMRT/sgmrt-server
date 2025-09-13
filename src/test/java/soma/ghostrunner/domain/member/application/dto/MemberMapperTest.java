package soma.ghostrunner.domain.member.application.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import soma.ghostrunner.domain.auth.api.dto.request.SignUpRequest;
import soma.ghostrunner.domain.member.api.dto.TermsAgreementDto;
import soma.ghostrunner.domain.member.domain.Gender;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.domain.MemberVdot;
import soma.ghostrunner.domain.member.domain.TermsAgreement;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MemberMapper 단위 테스트")
class MemberMapperTest {

    private MemberMapper memberMapper;
    private final LocalDateTime DUMMY_DATETIME = LocalDateTime.of(2025, 8, 8, 12 , 0);

    @BeforeEach
    void setUp() {
        // Spring 컨텍스트 없이 MapStruct가 생성한 구현체를 가져옵니다.
        memberMapper = Mappers.getMapper(MemberMapper.class);
    }

    @DisplayName("AuthId와 SignUpRequest, TermsAgreement를 MemberCreationRequest로 변환 시 모든 필드가 매핑된다.")
    @Test
    void toMemberCreationRequest() {
        // given
        String externalAuthId = "auth-id-12345";
        SignUpRequest signUpRequest = createSignUpRequest();
        TermsAgreement termsAgreement = createTermsAgreement();

        // when
        MemberCreationRequest result = memberMapper.toMemberCreationRequest(externalAuthId, signUpRequest, termsAgreement);

        // then
        assertThat(result.getExternalAuthId()).isEqualTo(externalAuthId);
        assertThat(result.getProfileImageUrl()).isEqualTo(signUpRequest.getProfileImageUrl());
        assertThat(result.getNickname()).isEqualTo(signUpRequest.getNickname());
        assertThat(result.getGender()).isEqualTo(signUpRequest.getGender());
        assertThat(result.getAge()).isEqualTo(signUpRequest.getAge());
        assertThat(result.getHeight()).isEqualTo(signUpRequest.getHeight());
        assertThat(result.getWeight()).isEqualTo(signUpRequest.getWeight());
        assertThat(result.getTermsAgreement()).isEqualTo(termsAgreement); // 객체 동등성 비교
    }

    @DisplayName("Member와 vdot 값을 MemberVdot으로 변환 시 모든 필드가 매핑된다.")
    @Test
    void toMemberVdot() {
        // given
        Member member = createMember();
        int vdot = 25;

        // when
        MemberVdot result = memberMapper.toMemberVdot(member, vdot);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getVdot()).isEqualTo(vdot);
        assertThat(result.getMember()).isSameAs(member);
    }

    // --- Helper Methods ---

    private SignUpRequest createSignUpRequest() {
        // SignUpRequest 생성에 필요한 TermsAgreementDto 생성
        TermsAgreementDto termsDto = new TermsAgreementDto(true, true, true, DUMMY_DATETIME);
        return new SignUpRequest(
                "아이유",
                "http://example.com/image.jpg",
                Gender.MALE,
                25,
                180,
                75,
                termsDto
        );
    }

    private TermsAgreement createTermsAgreement() {
        return TermsAgreement.createIfAllMandatoryTermsAgreed(
                true,
                true,
                true,
                DUMMY_DATETIME
        );
    }

    private Member createMember() {
        return Member.of("장원영", "http://example.com/image.jpg");
    }
}