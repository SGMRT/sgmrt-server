package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SoftDelete;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.global.common.BaseTimeEntity;

@Entity
@Table(name = "course")
@SoftDelete
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

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

    @Builder(access = AccessLevel.PRIVATE)
    private Course(CourseProfile courseProfile, Member member, String name, StartPoint startPoint, String pathData, Boolean isPublic) {
        this.courseProfile = courseProfile;
        this.member = member;
        this.startPoint = startPoint;
        this.pathData = pathData;
        this.name = name;
        this.isPublic = isPublic;
    }

    public static Course of(Member member, CourseProfile courseProfile, StartPoint startPoint, String pathData) {
        return Course.builder()
                .member(member)
                .courseProfile(courseProfile)
                .startPoint(startPoint)
                .pathData(pathData)
                .isPublic(false)
                .build();
    }

}
