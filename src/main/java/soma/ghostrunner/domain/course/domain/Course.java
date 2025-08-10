package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SoftDelete;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.global.common.BaseTimeEntity;

@Entity
@Table(name = "course")
@SoftDelete
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
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

    @Column(name = "path_data_saved_url", columnDefinition = "LONGTEXT")
    private String pathDataSavedUrl;

    @Builder(access = AccessLevel.PRIVATE)
    private Course(CourseProfile courseProfile, Member member, String name, StartPoint startPoint,
                   String pathDataSavedUrl, Boolean isPublic) {
        this.courseProfile = courseProfile;
        this.member = member;
        this.startPoint = startPoint;
        this.pathDataSavedUrl = pathDataSavedUrl;
        this.name = name;
        this.isPublic = isPublic;
    }

    public static Course of(Member member, CourseProfile courseProfile, StartPoint startPoint, String pathDataSavedUrl) {
        return Course.builder()
                .member(member)
                .courseProfile(courseProfile)
                .startPoint(startPoint)
                .pathDataSavedUrl(pathDataSavedUrl)
                .isPublic(false)
                .build();
    }

}
