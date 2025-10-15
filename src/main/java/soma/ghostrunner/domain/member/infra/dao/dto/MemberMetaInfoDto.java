package soma.ghostrunner.domain.member.infra.dao.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

@Getter
public class MemberMetaInfoDto {

    private Long memberId;
    private String uuid;
    private String profileUrl;

    @QueryProjection
    public MemberMetaInfoDto(Long memberId, String uuid, String profileUrl) {
        this.memberId = memberId;
        this.uuid = uuid;
        this.profileUrl = profileUrl;
    }

}
