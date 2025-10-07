package soma.ghostrunner.domain.course.application;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.course.dao.CourseRedisRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.*;
import soma.ghostrunner.domain.course.dto.query.CourseQueryModel;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.course.dto.response.*;
import soma.ghostrunner.domain.course.enums.CourseSortType;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.course.exception.CourseNotFoundException;
import soma.ghostrunner.domain.running.api.support.RunningApiMapper;
import soma.ghostrunner.domain.running.application.RunningQueryService;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseFacade {
    private final CourseService courseService;
    private final RunningQueryService runningQueryService;
    private final CourseRedisRepository courseRedisRepository;

    private final CourseMapper courseMapper;
    private final RunningApiMapper runningApiMapper;

    @Transactional(readOnly = true)
    public List<CourseMapResponse> findCoursesByPositionCached(Double lat, Double lng, Integer radiusM, CourseSortType sort,
                                                               CourseSearchFilterDto filters, String viewerUuid) {
        // 1. 코스 리스트 조회
        List<CoursePreviewDto> courses = courseService.findNearbyCourses(lat, lng, radiusM, sort, filters);
        List<Long> courseIds = courses.stream().map(CoursePreviewDto::id).toList();

        // 2. 캐시에서 코스 조회
        Map<Long, CourseQueryModel> cachedCourses = courseRedisRepository.findAllById(courseIds);
        List<CourseMapResponse> responses;

        // 3. 존재하지 않는 코스 ID 추출 및 DB에서 조회 후 캐시 Write
        List<Long> cacheMissedIds = filterCacheMissedIds(cachedCourses);
        if(!cacheMissedIds.isEmpty()) {
            // todo: miss된 id만 조회하도록 변경
            log.info("CourseFacade::findCoursesByPositionCached() - found cache miss for {} courses", cacheMissedIds.size());
            List<CourseQueryModel> cacheUpdateCourses = new ArrayList<>();
            responses = new ArrayList<>();
            for(var course: courses) {
                Page<CourseGhostResponse> rankers = runningQueryService.findTopRankingGhostsByCourseId(course.id(), 4);
                CourseGhostResponse ghostForUser = getGhostResponse(course.id(), viewerUuid);
                long runnersCount = rankers.getTotalElements();

                cacheUpdateCourses.add(courseMapper.toCourseQueryModel(course, rankers.getContent(), runnersCount));
                responses.add(courseMapper.toCourseMapResponse(course, rankers.getContent().stream().map(RunnerProfile::from).toList(), runnersCount, ghostForUser));
            }
            courseRedisRepository.saveAll(cacheUpdateCourses);
        } else {
            log.info("CourseFacade::findCoursesByPositionCached() - all courses cache hit. querying ghost");
            Map<Long, Running> memberBestRuns = runningQueryService.findBestRunningRecordsForCourses(courseIds, viewerUuid);
            responses = courses.stream().map(course -> {
                CourseGhostResponse ghostForUser = null;
                if (memberBestRuns.containsKey(course.id())) {
                    ghostForUser = runningApiMapper.toGhostResponse(memberBestRuns.get(course.id()));
                }

                CourseQueryModel cachedCourse = cachedCourses.get(course.id());
                return courseMapper.toCourseMapResponse(course, cachedCourse.topRunners(), cachedCourse.runnerCount(), ghostForUser);
            }).toList();
        }

        // 4. 응답을 만들어서 반환
        return responses;
    }

    @Transactional(readOnly = true)
    public List<CourseMapResponse> findCoursesByPosition(Double lat, Double lng, Integer radiusM, CourseSortType sort,
                                                         CourseSearchFilterDto filters, String viewerUuid) {
        // 범위 내의 코스를 가져온 후, 각 코스에 대해 Top 4 러닝기록을 조회하고 dto에 매핑해 반환
        List<CoursePreviewDto> courses = courseService.findNearbyCourses(lat, lng, radiusM, sort, filters);
        // todo: courses 개수만큼 순회하면서 쿼리를 실행하는 대신, Set(course_id)를 뽑아서 한 번의 쿼리로 집계한다.
        return courses.stream().map(course -> {
            Page<CourseGhostResponse> rankers = runningQueryService.findTopRankingGhostsByCourseId(course.id(), 4);
            CourseGhostResponse ghostForUser = getGhostResponse(course.id(), viewerUuid);
            long runnersCount = rankers.getTotalElements();
            return courseMapper.toCourseMapResponse(course, rankers.getContent().stream().map(RunnerProfile::from).toList(), runnersCount, ghostForUser);
        }).toList();
    }

    @Transactional(readOnly = true)
    public CourseDetailedResponse findCourse(Long courseId, String viewerUuid) {
        Course course = courseService.findCourseById(courseId);
        CourseRunStatisticsDto courseStatistics = runningQueryService.findCourseRunStatistics(courseId)
                .orElse(new CourseRunStatisticsDto());
        UserPaceStatsDto userPaceStats = runningQueryService.findUserPaceStatistics(courseId, viewerUuid)
                .orElse(new UserPaceStatsDto());
        String telemetryUrl = getTelemetryUrlFromCourse(course);
        CourseGhostResponse ghostForUser = getGhostResponse(courseId, viewerUuid);
        return courseMapper.toCourseDetailedResponse(course, telemetryUrl, courseStatistics, userPaceStats, ghostForUser);
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
        Running running = runningQueryService.findBestPublicRunForCourse(courseId, memberUuid).orElseThrow(RunningNotFoundException::new);
        Integer ranking = runningQueryService.findPublicRankForCourse(courseId, running);
        return courseMapper.toRankingResponse(running, ranking);
    }

    public List<CourseGhostResponse> findTopRankingGhosts(Long courseId, int count) {
        Page<CourseGhostResponse> rankedGhostsPage = runningQueryService.findTopRankingGhostsByCourseId(courseId, count);
        return rankedGhostsPage.getContent();
    }

    public List<CourseGhostResponse> findTopPercentageGhosts(Long courseId, double percentage) {
        Page<CourseGhostResponse> rankedGhostsPage = runningQueryService.findTopPercentageGhostsByCourseId(courseId, percentage);
        return rankedGhostsPage.getContent();
    }

    @Transactional(readOnly = true)
    public Page<CourseSummaryResponse> findCourseSummariesOfMember(String memberUuid, Pageable pageable) {
        // todo: 평균 데이터 캐싱 (Course 테이블에 저장 혹은 캐싱)
        Page<CourseWithMemberDetailsDto> courseDetails = courseService.findCoursesByMemberUuid(memberUuid, pageable);
        List<CourseSummaryResponse> results = new ArrayList<>();

        for(CourseWithMemberDetailsDto courseDto : courseDetails.getContent()) {
            CourseRunStatisticsDto courseStatistics = runningQueryService.findCourseRunStatistics(courseDto.getCourseId())
                    .orElse(new CourseRunStatisticsDto());
            CourseGhostResponse ghostForUser = getGhostResponse(courseDto.getCourseId(), memberUuid);
            results.add(courseMapper.toCourseSummaryResponse(courseDto, courseStatistics.getUniqueRunnersCount(),
                    courseStatistics.getTotalRunsCount(), courseStatistics.getAvgCompletionTime(),
                    courseStatistics.getAvgFinisherPace(), courseStatistics.getAvgFinisherCadence(), ghostForUser));
        }

        return new PageImpl<>(results, pageable, courseDetails.getTotalElements());
    }

    public CourseStatisticsResponse findCourseStatistics(Long courseId) {
        CourseRunStatisticsDto stats = runningQueryService.findCourseRunStatistics(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        return courseMapper.toCourseStatisticsResponse(stats);
    }

    private CourseGhostResponse getGhostResponse(Long courseId, String viewerUuid) {
        return runningQueryService.findBestPublicRunForCourse(courseId, viewerUuid)
                .map(runningApiMapper::toGhostResponse)
                .orElse(null);
    }

    private String getTelemetryUrlFromCourse(Course course) {
        if (course.getSource() == CourseSource.OFFICIAL) {
            return course.getCourseDataUrls().getRouteUrl();
        }


        return runningQueryService.findFirstRunning(course.getId())
                .map(running -> running.getRunningDataUrls().getInterpolatedTelemetryUrl())
                .orElseGet(() -> {
                    log.warn("CourseService: No running data found for course id {}", course.getId());
                    return null;
                });
    }

    private static List<Long> filterCacheMissedIds(Map<Long, CourseQueryModel> cachedCourses) {
        return cachedCourses.entrySet().stream()
                .filter(entry -> entry.getValue() == null)
                .map(Map.Entry::getKey)
                .toList();
    }

}
