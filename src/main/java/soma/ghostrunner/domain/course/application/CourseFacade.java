package soma.ghostrunner.domain.course.application;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.course.dao.CourseCacheRepository;
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

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseFacade {
    private final CourseService courseService;
    private final RunningQueryService runningQueryService;
    private final CourseCacheRepository courseCacheRepository;

    private final CourseMapper courseMapper;
    private final RunningApiMapper runningApiMapper;

    private static final int MAX_RUNNER_PROFILES_PER_COURSE = 4;

    @Transactional(readOnly = true)
    public List<CourseMapResponse> findCoursesByPositionCached(Double lat, Double lng, Integer radiusM, CourseSortType sort,
                                                               CourseSearchFilterDto filters, String viewerUuid) {
        // 범위 내 코스 리스트 조회
        List<CoursePreviewDto> courses = courseService.findNearbyCourses(lat, lng, radiusM, sort, filters);
        List<Long> courseIds = courses.stream().map(CoursePreviewDto::id).toList();

        // 캐시에서 코스 정보 조회
        Map<Long, CourseQueryModel> cachedCourseInfos = courseCacheRepository.findAllById(courseIds);

        // 캐시 히트 여부에 따라 처리 분기
        List<CoursePreviewDto> filteredCourses = limitCoursesForViewer(courses, viewerUuid, 10);
        List<CourseMapResponse> responses;
        List<Long> cacheMissedIds = filterCacheMissedIds(cachedCourseInfos);
        if(!cacheMissedIds.isEmpty()) {
            log.info("CourseFacade::findCoursesByPositionCached() - found cache miss for {} courses", cacheMissedIds.size());
            var cacheMissedCourses = filteredCourses.stream().filter(
                    c -> cacheMissedIds.contains(c.id())).toList();
            responses = handleCourseCacheMiss(viewerUuid, filteredCourses, cacheMissedCourses, cachedCourseInfos);
        } else {
            log.info("CourseFacade::findCoursesByPositionCached() - all {} courses cache hit. querying ghost", filteredCourses.size());
            responses = handleCourseCacheHit(viewerUuid, filteredCourses, courseIds, cachedCourseInfos);
        }

        return responses;
    }

    private List<CourseMapResponse> handleCourseCacheMiss(String viewerUuid, List<CoursePreviewDto> totalCourses,
                                                          List<CoursePreviewDto> cacheMissedCourses, Map<Long, CourseQueryModel> cachedCourses) {
        var totalCourseIds = totalCourses.stream().map(CoursePreviewDto::id).toList();
        var cacheMissedIds = cacheMissedCourses.stream().map(CoursePreviewDto::id).toList();
        // 코스 별 Top 4 러너 프로필 & 러너 수 & 본인 최고 기록 조회
        Map<Long, List<CourseRunDto>> topRunnersForCourse = runningQueryService.findTopRankingDistinctGhostsByCourseIds(cacheMissedIds, 4);
        Map<Long, Long> runnerCountsForCourse = runningQueryService.findPublicRunnersCountByCourseIds(cacheMissedIds);
        Map<Long, Running> memberBestRuns = runningQueryService.findBestRunningRecordsForCourses(totalCourseIds, viewerUuid); // 본인 최고 기록은 캐싱되지 않으므로, 모든 코스에 대해 조회
        // 캐시 저장
        Map<Long, CourseQueryModel> newlyCachedCourses = saveCoursesToCache(cacheMissedCourses, topRunnersForCourse, runnerCountsForCourse);
        // 기존 코스 순서에 맞춰 응답 반환
        List<CourseMapResponse> ret = new ArrayList<>();
        for (var course: totalCourses) {
            CourseQueryModel courseModel;
            if (newlyCachedCourses.containsKey(course.id())) {
                courseModel = newlyCachedCourses.get(course.id());
            } else if (cachedCourses.containsKey(course.id()) && cachedCourses.get(course.id()) != null) {
                courseModel = cachedCourses.get(course.id());
            } else {
                log.warn("CourseFacade::handleCourseCacheMiss() - course id {} not found in both cache and newly queried", course.id());
                continue;
            }
            // 코스 별 본인 고스트 매핑
            CourseGhostResponse ghostForUser = memberBestRuns.get(course.id()) != null
                    ? runningApiMapper.toGhostResponse(memberBestRuns.get(course.id()))
                    : null;
            ret.add(courseMapper.toCourseMapResponse(course, courseModel.topRunners(), courseModel.runnerCount(), ghostForUser));
        }
        return ret;
    }

    private Map<Long, CourseQueryModel> saveCoursesToCache(List<CoursePreviewDto> cacheMissedCourses,
                                                               Map<Long, List<CourseRunDto>> topRunnersForCourse,
                                                               Map<Long, Long> runnerCountsForCourse) {
        Map<Long, CourseQueryModel> coursesToBeCached = new HashMap<>();
        for (var course: cacheMissedCourses) {
            List<CourseRunDto> runners = topRunnersForCourse.getOrDefault(course.id(), List.of());
            Long runnersCount = runnerCountsForCourse.getOrDefault(course.id(), 0L);
            coursesToBeCached.put(course.id(), new CourseQueryModel(course.id(), course.name(),
                    runners.stream().map(RunnerProfile::from).toList(), Math.toIntExact(runnersCount)));
        }
        log.info("CourseFacade::saveCoursesToCache() - saving {} courses to cache", coursesToBeCached.size());
        courseCacheRepository.saveAll(coursesToBeCached.values().stream().toList());
        return coursesToBeCached;
    }

    private List<CourseMapResponse> handleCourseCacheHit(String viewerUuid, List<CoursePreviewDto> courses,
                                                         List<Long> courseIds, Map<Long, CourseQueryModel> cachedCourses) {
        // 코스 별 본인 고스트 조회
        Map<Long, Running> memberBestRuns = runningQueryService.findBestRunningRecordsForCourses(courseIds, viewerUuid);
        return courses.stream().map(course -> {
            CourseGhostResponse ghostForUser = null;
            if (memberBestRuns.containsKey(course.id())) {
                ghostForUser = runningApiMapper.toGhostResponse(memberBestRuns.get(course.id()));
            }

            CourseQueryModel cachedCourse = cachedCourses.get(course.id());
            return courseMapper.toCourseMapResponse(course, cachedCourse.topRunners(), cachedCourse.runnerCount(), ghostForUser);
        }).toList();
    }

    @Deprecated
    @Transactional(readOnly = true)
    public List<CourseMapResponse> findCoursesByPosition(Double lat, Double lng, Integer radiusM, CourseSortType sort,
                                                         CourseSearchFilterDto filters, String viewerUuid) {
        // 범위 내의 코스를 가져온 후, 각 코스에 대해 Top 4 러닝기록을 조회하고 dto에 매핑해 반환
        List<CoursePreviewDto> courses = courseService.findNearbyCourses(lat, lng, radiusM, sort, filters);
        List<CoursePreviewDto> filteredCourses = limitCoursesForViewer(courses, viewerUuid, 10);
        // todo: courses 개수만큼 순회하면서 쿼리를 실행하는 대신, Set(course_id)를 뽑아서 한 번의 쿼리로 집계한다.
        return filteredCourses.stream().map(course -> {
            List<CourseGhostResponse> rankers = runningQueryService.findTopRankingDistinctGhostsByCourseId(course.id(),
                    MAX_RUNNER_PROFILES_PER_COURSE);
            CourseGhostResponse ghostForUser = getGhostResponse(course.id(), viewerUuid);
            long runnersCount = getRunnersCount(course.id(), rankers);
            return courseMapper.toCourseMapResponse(course, rankers.stream().map(RunnerProfile::from).toList(), runnersCount, ghostForUser);
        }).toList();
    }

    private long getRunnersCount(Long courseId, List<CourseGhostResponse> rankers) {
        if (rankers.size() < MAX_RUNNER_PROFILES_PER_COURSE) {
            return rankers.size();
        } else {
            return runningQueryService.findPublicRunnersCount(courseId);
        }
    }

    /** 본인 코스 > RECOMMENDED 지정 코스 > 타 러너 코스 > 더미 코스 순으로 limit개 이하를 선택한다. */
    private List<CoursePreviewDto> limitCoursesForViewer(List<CoursePreviewDto> courses, String viewerUuid, int limit) {
        CourseMapHolder categorizedCourses = categorizeCourses(courses, viewerUuid);
        var finalIndices = new ArrayList<Integer>();

        // 본인의 코스 - 최대 절반까지 선택
        int userCnt = Math.min(limit / 2, categorizedCourses.usersCoursesMap().size());
        finalIndices.addAll(randomSelect(categorizedCourses.usersCoursesMap().keySet(), userCnt));

        // 추천 지정 코스 - 최대 1/5까지 선택
        int recommendedCnt = Math.min(limit / 5, categorizedCourses.recommendedCoursesMap().size());
        finalIndices.addAll(randomSelect(categorizedCourses.recommendedCoursesMap().keySet(), recommendedCnt));

        // 다른 사람 코스 - 남은 개수만큼 선택
        int otherCnt = Math.min(limit - finalIndices.size(), categorizedCourses.othersCoursesMap().size());
        finalIndices.addAll(randomSelect(categorizedCourses.othersCoursesMap().keySet(), otherCnt));

        // 더미 코스 - 남은 개수만큼 선택
        int dummyCnt = Math.min(limit - finalIndices.size(),  categorizedCourses.dummyCoursesMap().size());
        finalIndices.addAll(randomSelect(categorizedCourses.dummyCoursesMap().keySet(), dummyCnt));

        // courses 순서대로 정렬하고 CoursePreviewDto로 매핑하여 반환
        Collections.sort(finalIndices);
        return finalIndices.stream()
                .map(courses::get)
                .toList();
    }

    private record CourseMapHolder(
            Map<Integer, CoursePreviewDto> usersCoursesMap, // 본인 코스
            Map<Integer, CoursePreviewDto> recommendedCoursesMap, // 추천 코스
            Map<Integer, CoursePreviewDto> othersCoursesMap, // 타인 코스
            Map<Integer, CoursePreviewDto> dummyCoursesMap // 더미 코스
    ) {}

    /** courses의 dto를 순회하며 본인 코스 / 추천 코스 / 타인 코스 / 더미 코스 중 하나로 분류하여 CourseMapHolder에 담는다.  */
    private CourseMapHolder categorizeCourses(List<CoursePreviewDto> courses, String viewerUuid) {
        // 코스 분류 (key: courses의 인덱스, value: dto)
        var users = new HashMap<Integer, CoursePreviewDto>();
        var others = new HashMap<Integer, CoursePreviewDto>();
        var recommended = new HashMap<Integer, CoursePreviewDto>();
        var dummy = new HashMap<Integer, CoursePreviewDto>();

        int idx = 0;
        for (var course : courses) {
            if (course.source() == CourseSource.RECOMMENDED) recommended.put(idx, course);
            else if (course.source() == CourseSource.OFFICIAL) dummy.put(idx, course);
            else {
                if (course.ownerUuid() != null && course.ownerUuid().equals(viewerUuid)) users.put(idx, course);
                else others.put(idx, course);
            }
            idx++;
        }
        return new CourseMapHolder(users, recommended, others, dummy);
    }

    /** source 중 count 개를 랜덤으로 고른다. */
    private List<Integer> randomSelect(Collection<Integer> source, int count) {
        if (count <= 0) return Collections.emptyList();
        var indices = new ArrayList<>(source);
        Collections.shuffle(indices);
        return indices.stream().limit(count).toList();
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
        Sort defaultSort = Sort.by(Sort.Direction.ASC, "runningRecord.duration");
        Pageable pageable = PageRequest.of(0, count, defaultSort);
        return runningQueryService.findPublicGhostRunsByCourseId(courseId, pageable)
                .getContent();
    }

    public List<CourseGhostResponse> findTopPercentageGhosts(Long courseId, double percentage) {
        Page<CourseGhostResponse> rankedGhostsPage = runningQueryService.findTopPercentageGhostsByCourseId(courseId, percentage);
        return rankedGhostsPage.getContent();
    }

    @Transactional(readOnly = true)
    public Page<CourseSummaryResponse> findCourseSummariesOfMember(String memberUuid, Pageable pageable) {
        Page<CourseWithMemberDetailsDto> courseDetails = courseService.findCoursesByMemberUuid(memberUuid, pageable);
        List<CourseSummaryResponse> results = new ArrayList<>();

        for(CourseWithMemberDetailsDto courseDto : courseDetails.getContent()) {
//            CourseRunStatisticsDto courseStatistics = runningQueryService.findCourseRunStatistics(courseDto.getCourseId())
//                    .orElse(new CourseRunStatisticsDto());
            CourseRunStatisticsDto courseStatistics = getDummyCourseStatistics(); // 통계 더이상 필요 없음 - 프론트 하위호환성 고려하여 빈 객체 반환
            CourseGhostResponse ghostForUser = getGhostResponse(courseDto.getCourseId(), memberUuid);
            results.add(courseMapper.toCourseSummaryResponse(courseDto, courseStatistics.getUniqueRunnersCount(),
                    courseStatistics.getTotalRunsCount(), courseStatistics.getAvgCompletionTime(),
                    courseStatistics.getAvgFinisherPace(), courseStatistics.getAvgFinisherCadence(), ghostForUser));
        }

        return new PageImpl<>(results, pageable, courseDetails.getTotalElements());
    }

    private static CourseRunStatisticsDto getDummyCourseStatistics() {
        return new CourseRunStatisticsDto(0d, 0d, 0d, 0d, 0d, 0, 0);
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
