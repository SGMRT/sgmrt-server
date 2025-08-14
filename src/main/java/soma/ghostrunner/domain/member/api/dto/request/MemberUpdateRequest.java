package soma.ghostrunner.domain.member.api.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import soma.ghostrunner.domain.member.enums.Gender;

import java.util.Set;

@Getter
@Builder
@AllArgsConstructor
public class MemberUpdateRequest {
    private String nickname;

    private Gender gender;

    @Positive
    private Integer height;

    @Positive
    private Integer weight;

    @Positive
    private Integer age;

    private String profileImageUrl;

    private Set<UpdatedAttr> updateAttrs;

    public enum UpdatedAttr {
        NICKNAME,
        GENDER,
        AGE,
        HEIGHT,
        WEIGHT,
        PROFILE_IMAGE_URL
    }
}
