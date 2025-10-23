package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.global.common.BaseTimeEntity;

import java.util.List;

@Entity
@Getter
@Table(name = "course_read_model")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseReadModel extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", unique = true)
    private Long courseId;
    @Column(name = "course_name")
    private String courseName;
    @Enumerated(EnumType.STRING)
    private CourseSource source;

    @Column(name = "start_latitude")
    private Double startLatitude;
    @Column(name = "start_longtitude")
    private Double startLongitude;

    @Column(name = "distance_km")
    private Double distance;
    @Column(name = "elevation_average_m")
    private Double elevationAverage;

    @Column(name = "route_url")
    private String routeUrl;
    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "runners_count")
    private Long runnersCount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "memberId", column = @Column(name = "rank1_runner_id")),
            @AttributeOverride(name = "memberProfileUrl", column = @Column(name = "rank1_runner_profile_url"))
    })
    private RankSlot rank1;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "memberId", column = @Column(name = "rank2_runner_id")),
            @AttributeOverride(name = "memberProfileUrl", column = @Column(name = "rank2_runner_profile_url"))
    })
    private RankSlot rank2;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "memberId", column = @Column(name = "rank3_runner_id")),
            @AttributeOverride(name = "memberProfileUrl", column = @Column(name = "rank3_runner_profile_url"))
    })
    private RankSlot rank3;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "memberId", column = @Column(name = "rank4_runner_id")),
            @AttributeOverride(name = "memberProfileUrl", column = @Column(name = "rank4_runner_profile_url"))
    })
    private RankSlot rank4;

    @Builder(access = AccessLevel.PRIVATE)
    private CourseReadModel(
            Long courseId, String courseName, CourseSource source,
            Double startLatitude, Double startLongitude, Double distance, Double elevationAverage,
            String routeUrl, String thumbnailUrl, Long runnersCount,
            RankSlot rank1, RankSlot rank2, RankSlot rank3, RankSlot rank4
    ) {
            this.courseId = courseId;
            this.courseName = courseName;
            this.source = source;
            this.startLatitude = startLatitude;
            this.startLongitude = startLongitude;
            this.distance = distance;
            this.elevationAverage = elevationAverage;
            this.routeUrl = routeUrl;
            this.thumbnailUrl = thumbnailUrl;
            this.runnersCount = runnersCount;
            this.rank1 = rank1;
            this.rank2 = rank2;
            this.rank3 = rank3;
            this.rank4 = rank4;
    }

    public static CourseReadModel of(Course course) {
        return CourseReadModel.builder()
                .courseId(course.getId())
                .courseName(course.getName())
                .source(course.getSource())
                .startLatitude(course.getStartCoordinate().getLatitude())
                .startLongitude(course.getStartCoordinate().getLongitude())
                .distance(course.getCourseProfile().getDistance())
                .elevationAverage(course.getCourseProfile().getElevationAverage())
                .routeUrl(course.getOfficialTelemetryUrl())
                .thumbnailUrl(course.getCourseDataUrls().getThumbnailUrl())
                .runnersCount(1L)
                .rank1(null).rank2(null).rank3(null).rank4(null)
                .build();
    }

    public void updateRanking(List<RankSlot>slots) {
        this.rank1 = getOrNull(slots, 0);
        this.rank2 = getOrNull(slots, 1);
        this.rank3 = getOrNull(slots, 2);
        this.rank4 = getOrNull(slots, 3);
    }

    private RankSlot getOrNull(java.util.List<RankSlot> list, int idx) {
        return (list != null && idx < list.size()) ? list.get(idx) : null;
    }

    public void updateRunnersCount(long runnersCount) {
        this.runnersCount = runnersCount;
    }

    public void updateFirstRunner(RankSlot slot) {
        this.rank1 = slot;
    }

    public void updateCourseInfo(Course course) {
        this.courseName = course.getName();
    }

}
