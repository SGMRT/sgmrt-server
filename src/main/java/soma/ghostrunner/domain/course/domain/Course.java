package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SoftDelete;
import soma.ghostrunner.domain.course.enums.CourseSource;
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

    @Enumerated(EnumType.STRING)
    private CourseSource source;

    @Setter
    @Column
    private Boolean isPublic = false;

    @Embedded
    private CourseDataUrls courseDataUrls;

    @Builder(access = AccessLevel.PRIVATE)
    private Course(CourseProfile courseProfile, Member member, String name, Coordinate startCoordinate, CourseSource source, Boolean isPublic, CourseDataUrls courseDataUrls) {
        this.courseProfile = courseProfile;
        this.member = member;
        this.startCoordinate = startCoordinate;
        this.source = source == null ? CourseSource.USER : source;
        this.name = name;
        this.isPublic = isPublic;
        this.courseDataUrls = courseDataUrls;
    }

    public static Course of(Member member, String name, CourseProfile courseProfile, Coordinate startCoordinate,
                            CourseSource source, Boolean isPublic, CourseDataUrls courseDataUrls) {
        return Course.builder()
                .member(member)
                .name(name)
                .courseProfile(courseProfile)
                .startCoordinate(startCoordinate)
                .source(source)
                .isPublic(isPublic)
                .courseDataUrls(courseDataUrls)
                .build();
    }

    public static Course of(Member member, Double distance,
                            Double elevationAverage, Double elevationGain, Double elevationLoss,
                            Double startLatitude, Double startLongitude,
                            String pathDataSavedUrl, String checkpointsUrl, String thumbnailImageSavedUrl) {

        CourseProfile courseProfile = CourseProfile.of(distance, elevationAverage, elevationGain, elevationLoss);
        Coordinate startCoordinate = Coordinate.of(startLatitude, startLongitude);
        CourseDataUrls courseDataUrls1 = CourseDataUrls.of(pathDataSavedUrl, checkpointsUrl, thumbnailImageSavedUrl);

        return Course.builder()
                .member(member)
                .courseProfile(courseProfile)
                .startCoordinate(startCoordinate)
                .isPublic(false)
                .courseDataUrls(courseDataUrls1)
                .build();
    }

}
