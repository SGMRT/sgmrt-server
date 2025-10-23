package soma.ghostrunner.domain.course.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

@Getter
public class CourseRankInfo {

    private Long courseId;
    private Long memberId;
    private Long duration;
    private String memberProfileUrl;

    @QueryProjection
    public CourseRankInfo(Long courseId, Long memberId, Long duration, String memberProfileUrl) {
        this.courseId = courseId;
        this.memberId = memberId;
        this.duration = duration;
        this.memberProfileUrl = memberProfileUrl;
    }

    @QueryProjection
    public CourseRankInfo(Long courseId, Long memberId, String memberProfileUrl) {
        this.courseId = courseId;
        this.memberId = memberId;
        this.memberProfileUrl = memberProfileUrl;
    }

}
