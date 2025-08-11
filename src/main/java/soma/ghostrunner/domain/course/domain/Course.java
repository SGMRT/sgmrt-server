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
    private Coordinate startCoordinate;

    @Setter
    @Column
    private Boolean isPublic = false;

    @Column(name = "path_data_saved_url")
    private String pathDataSavedUrl;

    @Builder(access = AccessLevel.PRIVATE)
    private Course(CourseProfile courseProfile, Member member, String name,
                   Coordinate startCoordinate, String pathDataSavedUrl, Boolean isPublic) {
        this.courseProfile = courseProfile;
        this.member = member;
        this.startCoordinate = startCoordinate;
        this.pathDataSavedUrl = pathDataSavedUrl;
        this.name = name;
        this.isPublic = isPublic;
    }

    public static Course of(Member member, Double distance,
                            Double elevationAverage, Double elevationGain, Double elevationLoss,
                            Double startLatitude, Double startLongitude, String pathDataSavedUrl) {

        CourseProfile courseProfile = CourseProfile.of(distance, elevationAverage, elevationGain, elevationLoss);
        Coordinate startCoordinate = Coordinate.of(startLatitude, startLongitude);

        return Course.builder()
                .member(member)
                .courseProfile(courseProfile)
                .startCoordinate(startCoordinate)
                .pathDataSavedUrl(pathDataSavedUrl)
                .isPublic(false)
                .build();
    }

}
