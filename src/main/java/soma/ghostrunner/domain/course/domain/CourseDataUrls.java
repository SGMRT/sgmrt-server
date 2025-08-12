package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import soma.ghostrunner.domain.running.domain.RunningDataUrls;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseDataUrls {

    @Column(name = "path_data_saved_url")
    private String pathDataSavedUrl;

    @Column(name = "thumbnail_image_saved_url")
    private String thumbnailImageSavedUrl;

    @Builder(access = AccessLevel.PRIVATE)
    public CourseDataUrls(String pathDataSavedUrl, String thumbnailImageSavedUrl) {
        this.pathDataSavedUrl = pathDataSavedUrl;
        this.thumbnailImageSavedUrl = thumbnailImageSavedUrl;
    }

    static CourseDataUrls of(String pathDataSavedUrl, String thumbnailImageSavedUrl) {
        return CourseDataUrls.builder()
                .pathDataSavedUrl(pathDataSavedUrl)
                .thumbnailImageSavedUrl(thumbnailImageSavedUrl)
                .build();
    }

}
