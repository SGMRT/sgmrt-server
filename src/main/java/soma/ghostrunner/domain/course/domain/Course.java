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

    @Lob @Column(columnDefinition = "LONGTEXT")
    private String pathData;

    @Column
    private String routeUrl;

    @Column
    private String thumbnailUrl;

    @Builder(access = AccessLevel.PRIVATE)
    private Course(CourseProfile courseProfile, Member member, String name, StartPoint startPoint,
                   String pathData, Boolean isPublic, String routeUrl, String thumbnailUrl) {
        this.courseProfile = courseProfile;
        this.member = member;
        this.startPoint = startPoint;
        this.name = name;
        this.pathData = pathData;
        this.isPublic = isPublic;
        this.routeUrl = routeUrl;
        this.thumbnailUrl = thumbnailUrl;
    }

    public static Course of(Member member, CourseProfile courseProfile, StartPoint startPoint,
                            String pathData, String routeUrl, String thumbnailUrl) {
        return Course.builder()
                .member(member)
                .courseProfile(courseProfile)
                .startPoint(startPoint)
                .pathData(pathData)
                .routeUrl(routeUrl)
                .thumbnailUrl(thumbnailUrl)
                .isPublic(false)
                .build();
    }

}
