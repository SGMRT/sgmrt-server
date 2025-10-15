package soma.ghostrunner.domain.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import soma.ghostrunner.domain.course.enums.CourseSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class CourseMapResponse2 {

    private Long id;
    private String name;
    private CourseSource source;
    private Double startLat;
    private Double startLng;
    private String routeUrl;
    private LocalDateTime createdAt;

    private long runnersCount;
    private boolean hasMyRecord;
    private Double distance;
    private Double elevation;
    private String thumbnailUrl;

    private List<RunnerInfo> top4Runners;

    @Getter
    @AllArgsConstructor
    public static class RunnerInfo {
        private String uuid;
        private String profileUrl;
    }

    public static CourseMapResponse2 of(Long courseId, String name, CourseSource source,
                                        Double startLat, Double startLng, String routeUrl, LocalDateTime createdAt,
                                        long runnersCount, boolean hasMyRecord,
                                        Double distance, Double elevation, String thumbnailUrl) {
        return new CourseMapResponse2(courseId, name, source,
                startLat, startLng, routeUrl, createdAt, runnersCount, hasMyRecord,
                distance, elevation, thumbnailUrl, new ArrayList<>());
    }

    public void addRunnerInfo(String uuid, String profileUrl) {
        top4Runners.add(new RunnerInfo(uuid, profileUrl));
    }

}
