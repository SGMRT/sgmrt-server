package soma.ghostrunner.domain.member.api.dto.request;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import soma.ghostrunner.domain.member.enums.Gender;

import java.util.Set;

@Getter
@AllArgsConstructor
public class MemberUpdateRequest {
    private String nickname;

    private Gender gender;

    @PositiveOrZero
    private Integer height;

    @PositiveOrZero
    private Integer weight;

    private String profileImageUrl;

    private Set<UpdatedAttr> updateAttrs;

    public enum UpdatedAttr {
        NICKNAME,
        GENDER,
        HEIGHT,
        WEIGHT,
        PROFILE_IMAGE_URL
    }
}
