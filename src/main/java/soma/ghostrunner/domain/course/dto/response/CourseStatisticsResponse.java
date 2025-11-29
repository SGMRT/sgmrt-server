package soma.ghostrunner.domain.course.dto.response;

public record CourseStatisticsResponse(
        Double averageCompletionTime,
        Double averageFinisherPace,
        Double averageFinisherCadence,
        Double averageCaloriesBurned,
        Double lowestFinisherPace,
        Integer uniqueRunnersCount,
        Integer totalRunsCount
) {}
