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

    @Column
    private String name;

    @Embedded
    private CourseProfile courseProfile;

    @Embedded
    private StartPoint startPoint;

    @Lob @Column(name = "path_data", columnDefinition = "LONGTEXT")
    private String pathData;

    @Builder
    private Course(CourseProfile courseProfile, StartPoint startPoint, String pathData) {
        this.courseProfile = courseProfile;
        this.startPoint = startPoint;
        this.pathData = pathData;
    }

    public static Course of(CourseProfile courseProfile, StartPoint startPoint, String pathData) {
        return Course.builder()
                .courseProfile(courseProfile)
                .startPoint(startPoint)
                .pathData(pathData)
                .build();
    }

    public void setName(String name) {
        this.name = name;
    }
}
