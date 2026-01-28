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

    /**
     * QueryDSL용 생성자
     *
     * @param subscriptionDeleted CourseSubscription.deleted 값
     *                            - null: subscription 없음 → courseInfo = null
     *                            - true: 등록 해제됨 → courseInfo = null
     *                            - false: 활성 구독 → courseInfo 노출
     */
    @QueryProjection
    public RunInfo(Long runningId, String name, Long startedAt,
                   RunRecordInfo recordInfo, CourseInfo courseInfo,
                   Long ghostRunningId, String screenShotUrl,
                   Boolean subscriptionDeleted) {
        this.runningId = runningId;
        this.name = name;
        this.startedAt = startedAt;
        this.recordInfo = recordInfo;
        this.courseInfo = isSubscriptionActive(subscriptionDeleted) ? courseInfo : null;
        this.ghostRunningId = ghostRunningId;
        this.screenShotUrl = screenShotUrl;
    }

    /**
     * subscription이 활성 상태인지 확인
     * - subscriptionDeleted가 null이면 subscription 없음 → false
     * - subscriptionDeleted가 true면 등록 해제됨 → false
     * - subscriptionDeleted가 false면 활성 → true
     */
    private boolean isSubscriptionActive(Boolean subscriptionDeleted) {
        return Boolean.FALSE.equals(subscriptionDeleted);
    }

}
