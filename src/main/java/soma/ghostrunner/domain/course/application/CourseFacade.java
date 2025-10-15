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
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.api.support.RunningApiMapper;
import soma.ghostrunner.domain.running.application.RunningQueryService;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseFacade {

    private final CourseService courseService;
    private final RunningQueryService runningQueryService;
    private final MemberService memberService;

    private final CourseMapper courseMapper;
    private final RunningApiMapper runningApiMapper;

    private static final int MAX_RUNNER_PROFILES_PER_COURSE = 4;

    @Transactional(readOnly = true)
    public List<CourseMapResponse> findCoursesByPositionCached(Double lat, Double lng, Integer radiusM, CourseSortType sort,
                                                               CourseSearchFilterDto filters, String viewerUuid) {
        // 범위 내 코스 리스트 조회
        var courses = courseService.findNearbyCourses(lat, lng, radiusM, sort, filters);
        List<Long> courseIds = courses.stream().map(CoursePreviewDto::id).toList();

        // 캐시에서 코스 정보 조회
        Map<Long, CourseQueryModel> cachedCourseInfos = courseCacheRepository.findAllById(courseIds);

        // 캐시 히트 여부에 따라 처리 분기
        var filteredCourses = limitCoursesForViewer(courses, viewerUuid, 10);
        List<CourseMapResponse> responses;
        var cacheMissedIds = filterCacheMissedIds(cachedCourseInfos);
        if(!cacheMissedIds.isEmpty()) {
            log.info("CourseFacade::findCoursesByPositionCached() - found cache miss for {} courses", cacheMissedIds.size());
            responses = handleCourseCacheMiss(viewerUuid, filteredCourses);
        } else {
            log.info("CourseFacade::findCoursesByPositionCached() - all {} courses cache hit. querying ghost", filteredCourses.size());
            responses = handleCourseCacheHit(viewerUuid, filteredCourses, courseIds, cachedCourseInfos);
        }

        return responses;
    }

    private List<CourseMapResponse> handleCourseCacheMiss(String viewerUuid, List<CoursePreviewDto> courses) {
        // todo: miss된 id만 조회하도록 변경
        List<CourseMapResponse> responses;
        var cacheUpdateCourses = new ArrayList<CourseQueryModel>();
        responses = new ArrayList<>();
        for(var course: courses) {
            // 코스별 Top 4 러너 프로필 & 본인 고스트 조회
            List<CourseGhostResponse> rankers = runningQueryService.findTopRankingDistinctGhostsByCourseId(course.id(), 4);
            CourseGhostResponse ghostForUser = getGhostResponse(course.id(), viewerUuid);
            long runnersCount = getRunnersCount(course.id(), rankers);

            cacheUpdateCourses.add(courseMapper.toCourseQueryModel(course, rankers, runnersCount));
            responses.add(courseMapper.toCourseMapResponse(course, rankers.stream().map(RunnerProfile::from).toList(), runnersCount, ghostForUser));
        }
        courseCacheRepository.saveAll(cacheUpdateCourses);
        return responses;
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

    @Transactional(readOnly = true)
    public List<CourseMapResponse> findCoursesByPosition(Double lat, Double lng, Integer radiusM, CourseSortType sort,
                                                         CourseSearchFilterDto filters, String viewerUuid) {

        Member member = memberService.findMemberByUuid(viewerUuid);

        List<CoursePreviewDto> courses = courseService.searchCourses(lat, lng, radiusM, sort, filters);
        List<CoursePreviewDto> filteredCourses = limitCoursesForViewer(courses, viewerUuid, 10);

        return filteredCourses.stream().map(course -> {
            List<CourseGhostResponse> rankers = runningQueryService.findTopRankingDistinctGhostsByCourseId(course.id(),
                    MAX_RUNNER_PROFILES_PER_COURSE);

            List<Long> runnersIdInCourse = runningQueryService.findPublicRunnersInCourse(course.id());
            long runnersCount = runnersIdInCourse.size();
            boolean hasMyRecord = isMemberIdInRunnersIdInCourse(runnersIdInCourse, member.getId());

            return courseMapper.toCourseMapResponse(course, rankers, runnersCount, hasMyRecord);
        }).toList();
    }

    private boolean isMemberIdInRunnersIdInCourse(List<Long> runnersIdInCourse, Long memberId) {
        for (Long runnerId : runnersIdInCourse) {
            if (runnerId.equals(memberId)) {
                return true;
            }
        }
        return false;
    }

    /** 본인 코스, 주변 랜덤 코스를 고려하여 limit개 이하로 제한한다. */
    private List<CoursePreviewDto> limitCoursesForViewer(List<CoursePreviewDto> courses, String viewerUuid, int limit) {
        var usersCoursesMap = new HashMap<Integer, CoursePreviewDto>(); // 본인의 코스 (idx -> dto)
        var othersCoursesMap = new HashMap<Integer, CoursePreviewDto>(); // 다른 사람 코스 (idx -> dto)
        int idx = 0;
        for (var course : courses) {
            if (course.ownerUuid() != null && course.ownerUuid().equals(viewerUuid)) {
                usersCoursesMap.put(idx, course);
            } else {
                othersCoursesMap.put(idx, course);
            }
            idx++;
        }

        // 본인의 코스 - 최대 절반까지, 단 otherCourses가 부족한 경우 더 담음
        var userCourseIndices = usersCoursesMap.keySet().stream()
                .limit(Math.max(limit / 2, limit - othersCoursesMap.size()))
                .toList();

        // 주변 랜덤 코스 - 다른 사람 코스를 랜덤하게 선택
        var otherCourseIndices = new ArrayList<>(othersCoursesMap.keySet());
        Collections.shuffle(otherCourseIndices);
        var otherCourseRandomIndices = otherCourseIndices.stream()
                .limit(limit - userCourseIndices.size())
                .toList();

        // 인덱스 기존 순서대로 정렬 후 dto로 매핑하여 반환
        var finalIndices = new ArrayList<Integer>();
        finalIndices.addAll(userCourseIndices);
        finalIndices.addAll(otherCourseRandomIndices);
        Collections.sort(finalIndices);

        return finalIndices.stream()
                .map(courses::get)
                .toList();
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
