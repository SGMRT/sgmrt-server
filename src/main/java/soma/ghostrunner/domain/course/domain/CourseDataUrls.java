package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseDataUrls {

    @Column(name = "route_url")
    private String routeUrl;

    @Column(name = "checkpoints_url")
    private String checkpointsUrl;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Builder(access = AccessLevel.PRIVATE)
    public CourseDataUrls(String routeUrl, String thumbnailUrl, String checkpointsUrl) {
        this.routeUrl = routeUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.checkpointsUrl = checkpointsUrl;
    }

    public static CourseDataUrls of(String routeUrl, String checkpointUrl, String thumbnailUrl) {
        return CourseDataUrls.builder()
                .routeUrl(routeUrl)
                .checkpointsUrl(checkpointUrl)
                .thumbnailUrl(thumbnailUrl)
                .build();
    }

}
