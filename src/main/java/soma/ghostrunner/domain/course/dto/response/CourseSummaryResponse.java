package soma.ghostrunner.domain.course.dto.response;


import java.time.LocalDateTime;

public record CourseSummaryResponse (
        Long id,
        String name,
        String thumbnailUrl,
        Integer elevationGain,
        LocalDateTime createdAt,
        Integer uniqueRunnersCount,
        Integer totalRunsCount,
        Integer distance,
        Integer averageCompletionTime,
        Double averageFinisherPace,
        Integer averageFinisherCadence,
        CourseGhostResponse myGhostInfo,
        Boolean isPublic
) {}
