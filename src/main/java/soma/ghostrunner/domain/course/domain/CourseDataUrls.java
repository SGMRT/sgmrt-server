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

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Builder(access = AccessLevel.PRIVATE)
    public CourseDataUrls(String routeUrl, String thumbnailUrl) {
        this.routeUrl = routeUrl;
        this.thumbnailUrl = thumbnailUrl;
    }

    static CourseDataUrls of(String routeUrl, String thumbnailUrl) {
        return CourseDataUrls.builder()
                .routeUrl(routeUrl)
                .thumbnailUrl(thumbnailUrl)
                .build();
    }

}
