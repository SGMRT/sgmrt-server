package soma.ghostrunner.domain.running.application.dto.response;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

@Getter
public class MemberAndRunRecordInfo {

    private String nickname;
    private String profileUrl;
    private RunRecordInfo recordInfo;

    @QueryProjection
    public MemberAndRunRecordInfo(String nickname, String profileUrl, RunRecordInfo recordInfo) {
        this.nickname = nickname;
        this.profileUrl = profileUrl;
        this.recordInfo = recordInfo;
    }
}
