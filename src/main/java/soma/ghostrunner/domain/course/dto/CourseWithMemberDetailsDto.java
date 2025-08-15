package soma.ghostrunner.domain.course.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class CourseWithMemberDetailsDto {
    // Course 정보
    private Long courseId;
    private String courseName;
    private String courseThumbnailUrl;
    private Double startLat;
    private Double startLng;
    private Integer distance;
    private Integer elevationGain;
    private Integer elevationLoss;
    private Boolean courseIsPublic;
    private LocalDateTime courseCreatedAt;

    // Member 정보
    private Long memberId;
    private String memberUuid;
    private String memberNickname;
    private String memberProfileImageUrl;
}
