package soma.ghostrunner.domain.course.application;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.course.dao.CourseCacheRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.*;
import soma.ghostrunner.domain.course.dto.query.CourseMapDto;
import soma.ghostrunner.domain.course.dto.query.CourseQueryModel;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.course.dto.response.*;
import soma.ghostrunner.domain.course.enums.CourseSortType;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.course.exception.CourseNotFoundException;
import soma.ghostrunner.domain.course.exception.RegionNotFoundException;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.api.support.RunningApiMapper;
import soma.ghostrunner.domain.running.application.RunningQueryService;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseFacade {

    private static final int MAX_COURSES_PER_MAP_RESPONSE = 10;

    /**
     * 지역 캐시 경로를 허용하는 요청 반경 상한.
     *
     * <p>지역 캐시 값은 대표좌표 기준 <b>고정 2km</b>다(설계 cache/05 §4). 그보다 넓은 뷰포트 요청에
     * 2km 결과를 돌려주면 예외도 로그도 없는 침묵 오답이 되므로, 상한을 넘는 요청은 캐시 경로 자체를 포기한다.
     * 3000 = 고정값 2km + 뷰포트 오차 여유. FE의 "지도 중심 ≈ 사용자 GPS" 규율에 정확성을 의존하지 않기 위한
     * 서버 자체 방어이며, 상한 초과는 에러가 아니라 정상 폴백이다.</p>
     */
    private static final int MAX_CACHEABLE_RADIUS_M = 3000;

    private final CourseService courseService;
    private final RunningQueryService runningQueryService;
    private final CourseCacheRepository courseCacheRepository;
    private final CourseReadModelReader courseReadModelReader;
  
    private final MemberService memberService;

    private final CourseMapper courseMapper;
    private final RunningApiMapper runningApiMapper;

    /**
     * @deprecated 수동 Redis 캐시(course:{id}) 기반 구 조회 경로. {@link #findCoursesByPosition}(리드모델 +
     *             Spring Cache)로 대체되었다. 신경로 안정화 후 {@link soma.ghostrunner.domain.course.dao.CourseCacheRepository},
     *             {@link CourseCacheEventListener}와 함께 제거 예정.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public List<CourseMapResponse> findCoursesByPositionCached(Double lat, Double lng, Integer radiusM, CourseSortType sort,
                                                               CourseSearchFilterDto filters, String viewerUuid) {
        // 범위 내 코스 리스트 조회
        Member viewer = findMemberByUuid(viewerUuid);
        List<CoursePreviewDto> courses = courseService.findNearbyCourses(lat, lng, radiusM, sort, filters, viewer.getId());
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

    /**
     * 주변 코스 지도 조회 — 리드모델 + Spring Cache 경로. (설계 04 §0-2, cache/05 §5-2·§6-6)
     *
     * <p>흐름: Reader(캐시/리드모델 쿼리) → 랜덤 선별(매 요청, 캐시 밖) → 내 고스트 조회(선별분만)
     * → 응답 조립. checkpointsUrl/createdAt 은 클라 미사용으로 null.</p>
     *
     * <p>{@code regionId}(선택 파라미터) 경우별 동작은 설계 cache/05 §5-2 참고.</p>
     *
     * <p>정렬/필터 파라미터는 하위호환으로 받되 적용하지 않는다(클라 미사용 확인 — 설계 §1 확정).
     * 기본값이 아닌 요청은 캐시만 우회한다.</p>
     */
    @Transactional(readOnly = true)
    public List<CourseMapResponse> findCoursesByPosition(Double lat, Double lng, Integer radiusM, CourseSortType sort,
                                                         CourseSearchFilterDto filters, Long regionId, String viewerUuid) {
        List<CourseMapDto> candidateCourses = findCandidateCourses(lat, lng, radiusM, sort, filters, regionId);

        // 랜덤 선별 — 사용자별 다양성이 목적이므로 캐시된 원본 리스트 위에서 매 요청 수행한다.
        List<CoursePreviewDto> previews = candidateCourses.stream()
                .map(CourseMapDto::toPreviewDto)
                .toList();
        List<CoursePreviewDto> selectedCourses = limitCoursesForViewer(previews, viewerUuid, MAX_COURSES_PER_MAP_RESPONSE);

        // 내 고스트는 개인화 데이터라 캐싱 대상이 아니다 — 선별된 코스에 대해서만 조회한다.
        List<Long> selectedCourseIds = selectedCourses.stream().map(CoursePreviewDto::id).toList();
        Map<Long, Running> memberBestRuns = runningQueryService.findBestRunningRecordsForCourses(selectedCourseIds, viewerUuid);

        Map<Long, CourseMapDto> candidateCourseById = candidateCourses.stream()
                .collect(Collectors.toMap(CourseMapDto::courseId, dto -> dto));

        List<CourseMapResponse> responses = new ArrayList<>();
        for (CoursePreviewDto selectedCourse : selectedCourses) {
            Running myBestRun = memberBestRuns.get(selectedCourse.id());
            CourseGhostResponse myGhost = myBestRun != null ? runningApiMapper.toGhostResponse(myBestRun) : null;
            responses.add(candidateCourseById.get(selectedCourse.id()).toResponse(myGhost));
        }
        return responses;
    }

    /**
     * 조회 경로 선택 — 지역 캐시 경로, 그리고 실패 시 요청 좌표 폴백.
     *
     * <p>미발급 regionId는 홈 화면을 막지 않는다(설계 §5-1). dev는 {@code ddl-auto: create}라 배포마다
     * region 테이블이 비워지는 반면 FE는 regionId를 persist하므로, "발급된 적 없는 regionId"는 확정 재현된다.
     * 좌표 폴백이라는 완전한 복구 경로가 이미 있으므로 조용히 강등한다.</p>
     *
     * <p>catch를 <b>Facade(= {@code @Cacheable} 프록시 바깥)</b>에 두는 것이 핵심이다. Reader 안에서 잡으면
     * 캐시 프록시가 폴백 결과를 {@code course-map::{regionId}}에 적재해, 없는 지역의 좌표 기반 결과가
     * 캐시에 오염 적재된다. 예외가 프록시를 뚫고 나가면 Spring Cache는 적재하지 않는다.</p>
     */
    private List<CourseMapDto> findCandidateCourses(Double lat, Double lng, Integer radiusM, CourseSortType sort,
                                                    CourseSearchFilterDto filters, Long regionId) {
        if (!useRegionCache(regionId, radiusM, sort, filters)) {
            return courseReadModelReader.findCoursesForMap(lat, lng, radiusM);
        }
        try {
            return courseReadModelReader.findCoursesForMapByRegion(regionId);
        } catch (RegionNotFoundException unknownRegion) {
            log.warn("CourseFacade::findCoursesByPosition() - unknown regionId {}, fallback to coordinates", regionId);
            return courseReadModelReader.findCoursesForMap(lat, lng, radiusM);
        }
    }

    /**
     * 지역 캐시 경로 판정 — 세 조건을 모두 만족해야 캐시를 읽고 적재한다.
     * ① regionId 첨부 ② 요청 반경이 캐시 값의 고정 2km와 어긋나지 않을 만큼 좁음({@link #MAX_CACHEABLE_RADIUS_M})
     * ③ 기본 요청(정렬·필터 기본값) — 비기본 요청까지 같은 키에 실으면 캐시 값의 결정성이 깨진다.
     */
    private boolean useRegionCache(Long regionId, Integer radiusM, CourseSortType sort, CourseSearchFilterDto filters) {
        return regionId != null
                && (radiusM == null || radiusM <= MAX_CACHEABLE_RADIUS_M)
                && isDefaultMapRequest(sort, filters);
    }

    /** 캐시 대상 판정 — 클라이언트가 실제로 쓰는 기본 요청(거리 정렬 + 필터 없음)만 캐싱한다. */
    private boolean isDefaultMapRequest(CourseSortType sort, CourseSearchFilterDto filters) {
        boolean noFilters = filters == null
                || (filters.getMinDistanceM() == null && filters.getMaxDistanceM() == null
                    && filters.getMinElevationM() == null && filters.getMaxElevationM() == null
                    && filters.getOwnerUuid() == null);
        return (sort == null || sort == CourseSortType.DISTANCE) && noFilters;
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

    public void updateCourse(Long courseId, CoursePatchRequest request, String memberUuid) {
        courseService.updateCourse(courseId, request, memberUuid);
    }

    public void deleteCourse(Long courseId, String memberUuid) {
        courseService.deleteCourse(courseId, memberUuid);
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
            CourseRunStatisticsDto courseStatistics = runningQueryService.findCourseRunStatistics(courseDto.getCourseId())
                    .orElse(new CourseRunStatisticsDto());
            courseStatistics = switchTotalRunsCountToUniqueRunnersCount(courseStatistics);
            CourseGhostResponse ghostForUser = getGhostResponse(courseDto.getCourseId(), memberUuid);
            results.add(courseMapper.toCourseSummaryResponse(courseDto, courseStatistics.getUniqueRunnersCount(),
                    courseStatistics.getTotalRunsCount(), courseStatistics.getAvgCompletionTime(),
                    courseStatistics.getAvgFinisherPace(), courseStatistics.getAvgFinisherCadence(), ghostForUser));
        }

        return new PageImpl<>(results, pageable, courseDetails.getTotalElements());
    }

    private Member findMemberByUuid(String memberUuid) {
        return memberService.findMemberByUuid(memberUuid);
    }

    // totalRunsCount 대신 uniqueRunnersCount를 할당하여 반환 (프론트 요청)
    private CourseRunStatisticsDto switchTotalRunsCountToUniqueRunnersCount(CourseRunStatisticsDto courseStatistics) {
        int uniqueRunnersCount = courseStatistics.getUniqueRunnersCount();
        return new CourseRunStatisticsDto(
                courseStatistics.getAvgCompletionTime(),
                courseStatistics.getAvgFinisherPace(),
                courseStatistics.getAvgFinisherCadence(),
                courseStatistics.getAvgCaloriesBurned(),
                courseStatistics.getLowestFinisherPace(),
                uniqueRunnersCount,
                uniqueRunnersCount // totalRunsCount 대신 uniqueRunnersCount 할당
        );
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
