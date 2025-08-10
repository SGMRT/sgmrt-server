package soma.ghostrunner.domain.course.application;


import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.CourseMapper;
import soma.ghostrunner.domain.course.dto.CourseWithCoordinatesDto;
import soma.ghostrunner.domain.course.dto.CourseRunStatisticsDto;
import soma.ghostrunner.domain.course.dto.CourseWithMemberDetailsDto;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.course.dto.response.*;
import soma.ghostrunner.domain.running.application.RunningQueryService;
import soma.ghostrunner.domain.running.application.RunningTelemetryQueryService;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;
import soma.ghostrunner.domain.running.domain.Running;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseFacade {
    private final CourseService courseService;
    private final RunningQueryService runningQueryService;
    private final RunningTelemetryQueryService runningTelemetryQueryService;

    private final CourseMapper courseMapper;

    @Transactional(readOnly = true)
    public List<CourseMapResponse> findCoursesByPosition(
            Double lat, Double lng, Integer radiusM,
            Integer minDistanceM, Integer maxDistanceM,
            Integer minElevationM, Integer maxElevationM,
            String ownerUuid) {
        // 범위 내의 코스를 가져온 후, 각 코스에 대해 Top 4 러닝기록을 조회하고 dto에 매핑해 반환
        List<CourseWithCoordinatesDto> courses = courseService.searchCourses(lat, lng, radiusM,
                minDistanceM, maxDistanceM, minElevationM, maxElevationM, ownerUuid);
        return courses.stream().map(course -> {
            Page<CourseGhostResponse> rankers = runningQueryService.findTopRankingGhostsByCourseId(course.id(), 4);
            long runnersCount = rankers.getTotalElements();
            return courseMapper.toCourseMapResponse(course, rankers.getContent(), runnersCount);
        }).toList();
    }

    @Transactional(readOnly = true)
    public CourseDetailedResponse findCourse(Long courseId) {
        Course course = courseService.findCourseById(courseId);
        CourseRunStatisticsDto courseStatistics = runningQueryService.findCourseRunStatistics(courseId)
                .orElse(new CourseRunStatisticsDto());
        return courseMapper.toCourseDetailedResponse(
                course,
                courseStatistics.getAvgCompletionTime(), courseStatistics.getAvgFinisherPace(),
                courseStatistics.getAvgFinisherCadence(), courseStatistics.getLowestFinisherPace());
    }

    public void updateCourse(Long courseId, CoursePatchRequest request) {
        courseService.updateCourse(courseId, request);
    }

    public void deleteCourse(Long courseId) {
        courseService.deleteCourse(courseId);
    }

    public Page<CourseGhostResponse> findPublicGhosts(Long courseId, Pageable pageable) {
        return runningQueryService.findPublicGhostRunsByCourseId(courseId, pageable);
    }

    @Transactional(readOnly = true)
    public CourseRankingResponse findCourseRankingDetail(Long courseId, String memberUuid) {
        Running running = runningQueryService.findBestPublicRunForCourse(courseId, memberUuid);
        Integer ranking = runningQueryService.findPublicRankForCourse(courseId, running);
        return courseMapper.toRankingResponse(running, ranking);
    }

    public List<CourseGhostResponse> findTopRankingGhosts(Long courseId, int count) {
        Page<CourseGhostResponse> rankedGhostsPage = runningQueryService.findTopRankingGhostsByCourseId(courseId, count);
        return rankedGhostsPage.getContent();
    }

    @Transactional(readOnly = true)
    public CourseCoordinatesResponse findCourseFirstRunCoordinatesWithDetails(Long courseId) {
        Course course = courseService.findCourseById(courseId);
        Running firstRun = runningQueryService.findFirstRunning(courseId);
        List<CoordinateDto> coordinates = runningTelemetryQueryService.findCoordinateTelemetries(firstRun.getId(), firstRun.getTelemetrySavedUrl());
        return courseMapper.toCoordinatesResponse(course, coordinates);
    }

    @Transactional(readOnly = true)
    public Page<CourseSummaryResponse> findCourseSummariesOfMember(String memberUuid, Pageable pageable) {
        // todo: 평균 데이터 캐싱 (Course 테이블에 저장 혹은 캐싱)
        Page<CourseWithMemberDetailsDto> courseDetails = courseService.findCoursesByMemberUuid(memberUuid, pageable);
        List<CourseSummaryResponse> results = new ArrayList<>();

        for(CourseWithMemberDetailsDto courseDto : courseDetails.getContent()) {
            CourseRunStatisticsDto courseStatistics = runningQueryService.findCourseRunStatistics(courseDto.getCourseId())
                    .orElse(new CourseRunStatisticsDto());
            results.add(courseMapper.toCourseSummaryResponse(courseDto, courseStatistics.getUniqueRunnersCount(),
                    courseStatistics.getTotalRunsCount(), courseStatistics.getAvgCompletionTime(),
                    courseStatistics.getAvgFinisherPace(), courseStatistics.getAvgFinisherCadence()));
        }

        return new PageImpl<>(results, pageable, courseDetails.getTotalElements());
    }

}
