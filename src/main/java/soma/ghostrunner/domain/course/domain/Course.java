package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.*;
import lombok.*;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.global.common.BaseTimeEntity;

@Entity
@Table(name = "course")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "owner_id")
    private Member owner;

    @Setter
    @Column
    private String name;

    @Embedded
    private CourseProfile courseProfile;

    @Embedded
    private StartPoint startPoint;

    @Setter
    @Column
    private Boolean isPublic = false;

    @Lob @Column(name = "path_data", columnDefinition = "LONGTEXT")
    private String pathData;

    @Builder
    private Course(CourseProfile courseProfile, Member owner, String name, StartPoint startPoint, String pathData, Boolean isPublic) {
        this.courseProfile = courseProfile;
        this.owner = owner;
        this.startPoint = startPoint;
        this.pathData = pathData;
        this.name = name;
        this.isPublic = isPublic;
    }

    public static Course of(CourseProfile courseProfile, StartPoint startPoint, String pathData) {
        return Course.builder()
                .courseProfile(courseProfile)
                .startPoint(startPoint)
                .pathData(pathData)
                .isPublic(false)
                .build();
    }

    public static Course of(CourseProfile courseProfile, String name, StartPoint startPoint, String pathData, Boolean isPublic) {
        return Course.builder()
                .courseProfile(courseProfile)
                .name(name)
                .startPoint(startPoint)
                .pathData(pathData)
                .isPublic(isPublic)
                .build();
    }

    public static Course of(CourseProfile courseProfile, Member owner, String name, StartPoint startPoint, String pathData, Boolean isPublic) {
        return Course.builder()
            .courseProfile(courseProfile)
            .owner(owner)
            .name(name)
            .startPoint(startPoint)
            .pathData(pathData)
            .isPublic(isPublic)
            .build();
    }

}
