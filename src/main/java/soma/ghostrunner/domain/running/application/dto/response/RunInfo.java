package soma.ghostrunner.domain.running.application.dto.response;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import soma.ghostrunner.domain.running.domain.Running;

@Getter
public class RunInfo {

    private Long runningId;
    private String name;
    private Long startedAt;
    private RunRecordInfo recordInfo;
    private CourseInfo courseInfo;
    private Long ghostRunningId;
    private String screenShotUrl;

    public RunInfo(Running running) {
        this.runningId = running.getId();
        this.name = running.getRunningName();
        this.startedAt = running.getStartedAt();
        this.recordInfo = new RunRecordInfo(running.getRunningRecord());
        this.ghostRunningId = running.getGhostRunningId();
        this.screenShotUrl = running.getRunningDataUrls().getScreenShotUrl();
    }

    @QueryProjection
    public RunInfo(Long runningId, String name, Long startedAt,
                   RunRecordInfo recordInfo, CourseInfo courseInfo,
                   Long ghostRunningId, String screenShotUrl) {
        this.runningId = runningId;
        this.name = name;
        this.startedAt = startedAt;
        this.recordInfo = recordInfo;
        this.courseInfo = courseInfo.getIsPublic() ? courseInfo : null;
        this.ghostRunningId = ghostRunningId;
        this.screenShotUrl = screenShotUrl;
    }

}
