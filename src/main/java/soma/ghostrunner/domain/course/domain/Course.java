package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.*;
import lombok.*;
import soma.ghostrunner.global.common.BaseTimeEntity;

@Entity
@Table(name = "course")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Embedded
    private CourseMetaInfo courseMetaInfo;

    @Embedded
    private StartPoint startPoint;

    @Lob @Column(name = "path_data", columnDefinition = "LONGTEXT")
    private String pathData;

    @Builder
    private Course(String name, CourseMetaInfo courseMetaInfo, StartPoint startPoint, String pathData) {
        this.name = name;
        this.courseMetaInfo = courseMetaInfo;
        this.startPoint = startPoint;
        this.pathData = pathData;
    }

    public static Course of(String name, CourseMetaInfo courseMetaInfo, StartPoint startPoint, String pathData) {
        return Course.builder()
                .name(name)
                .courseMetaInfo(courseMetaInfo)
                .startPoint(startPoint)
                .pathData(pathData)
                .build();
    }
}
