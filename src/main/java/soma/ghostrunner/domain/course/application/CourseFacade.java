package soma.ghostrunner.domain.course.application;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.*;
import soma.ghostrunner.domain.course.dto.query.CourseMapDto;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.course.dto.response.*;
import soma.ghostrunner.domain.course.enums.CourseSortType;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.course.enums.GhostSortType;
import soma.ghostrunner.domain.course.exception.CourseNotFoundException;
import soma.ghostrunner.domain.running.api.support.RunningApiMapper;
import soma.ghostrunner.domain.running.application.RunningReader;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseFacade {

    private static final int MAX_COURSES_PER_MAP_RESPONSE = 10;

    private final CourseReader courseReader;
    private final CourseWriter courseWriter;
    private final RunningReader runningReader;
    private final CourseReadModelReader courseReadModelReader;

    private final CourseMapper courseMapper;
    private final RunningApiMapper runningApiMapper;

    /**
     * 주변 코스 지도 조회 — 리드모델 기반 경로.
     * (설계 문서: docs/design/course-cell-bucket-cache-design.md §3-10 · §3-12)
     *
     * <p>흐름: Reader(리드모델 조회) → 랜덤 선별(매 요청) → 내 고스트 조회(선별분만) → 응답 조립.
     * checkpointsUrl/createdAt 은 클라 미사용으로 null.</p>
     *
     * <p>이 메서드는 캐시를 알지 못한다 — 캐시 적중 여부·경로 판정은 전적으로
     * {@link CourseReadModelReader}의 책임이고, Facade에는 선별과 조립만 남는다 (설계 §3-12).</p>
     *
     * <p>{@code regionId}는 배포된 FE가 계속 실어 보내므로 시그니처에 남기되(외부 API 불변),
     * 조회에는 사용하지 않는다 — 결과는 오직 요청 좌표·반경으로 결정된다 (설계 §3-10).</p>
     *
     * <p>정렬/필터 파라미터도 하위호환으로 받되 적용하지 않는다
     * (클라 미사용 확인 — docs/refactoring/course-read-model/core/04-detailed-design.md §1).</p>
     */
    @Transactional(readOnly = true)
    public List<CourseMapResponse> findCoursesByPosition(Double lat, Double lng, Integer radiusM, CourseSortType sort,
                                                         CourseSearchFilterDto filters, Long regionId, String viewerUuid) {
        List<CourseMapDto> candidateCourses = courseReadModelReader.findCoursesForMap(lat, lng, radiusM);

        // 랜덤 선별 — 사용자별 다양성이 목적이므로 조회 결과 위에서 매 요청 수행한다.
        List<CoursePreviewDto> previews = candidateCourses.stream()
                .map(CourseMapDto::toPreviewDto)
                .toList();
        List<CoursePreviewDto> selectedCourses = limitCoursesForViewer(previews, viewerUuid, MAX_COURSES_PER_MAP_RESPONSE);

        // 내 고스트는 뷰어별 개인화 데이터다 — 선별된 코스에 대해서만 조회한다.
        List<Long> selectedCourseIds = selectedCourses.stream().map(CoursePreviewDto::id).toList();
        Map<Long, Running> memberBestRuns = runningReader.findBestRunningRecordsForCourses(selectedCourseIds, viewerUuid);

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
        Course course = courseReader.findCourseById(courseId);
        CourseRunStatisticsDto courseStatistics = runningReader.findCourseRunStatistics(courseId)
                .orElse(new CourseRunStatisticsDto());
        UserPaceStatsDto userPaceStats = runningReader.findUserPaceStatistics(courseId, viewerUuid)
                .orElse(new UserPaceStatsDto());
        String telemetryUrl = getTelemetryUrlFromCourse(course);
        CourseGhostResponse ghostForUser = getGhostResponse(courseId, viewerUuid);
        return courseMapper.toCourseDetailedResponse(course, telemetryUrl, courseStatistics, userPaceStats, ghostForUser);
    }

    public void updateCourse(Long courseId, CoursePatchRequest request, String memberUuid) {
        courseWriter.updateCourse(courseId, request, memberUuid);
    }

    public void deleteCourse(Long courseId, String memberUuid) {
        courseWriter.deleteCourse(courseId, memberUuid);
    }

    /**
     * 코스의 공개 고스트 페이징.
     *
     * <p>정렬 필드 검증({@link GhostSortType})도 {@link CourseGhostResponse} 매핑도 course 도메인의 규칙이므로
     * 여기서 한다 — {@link RunningReader}는 {@code Page<Running>}까지만 책임진다.
     * 매핑이 Reader의 트랜잭션 밖에서 도는데도 안전한 이유는 러너(member)가 fetch join으로 함께 적재되기 때문이다.
     * (지연 로딩 연관을 새로 읽는 매핑을 추가한다면 이 메서드에 {@code @Transactional(readOnly = true)}가 필요해진다.)
     */
    public Page<CourseGhostResponse> findPublicGhosts(Long courseId, Pageable pageable) {
        validateGhostSortProperty(pageable);
        return runningReader.findPublicGhostRuns(courseId, pageable)
                .map(runningApiMapper::toGhostResponse);
    }

    private void validateGhostSortProperty(Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!GhostSortType.isValidField(order.getProperty())) {
                throw new IllegalArgumentException("잘못된 고스트 정렬 필드");
            }
        });
    }

    @Transactional(readOnly = true)
    public CourseRankingResponse findCourseRankingDetail(Long courseId, String memberUuid) {
        Running running = runningReader.findBestPublicRunForCourse(courseId, memberUuid).orElseThrow(RunningNotFoundException::new);
        Integer ranking = runningReader.findPublicRankForCourse(courseId, running);
        return courseMapper.toRankingResponse(running, ranking);
    }

    public List<CourseGhostResponse> findTopRankingGhosts(Long courseId, int count) {
        Sort defaultSort = Sort.by(Sort.Direction.ASC, "runningRecord.duration");
        Pageable pageable = PageRequest.of(0, count, defaultSort);
        return findPublicGhosts(courseId, pageable).getContent();
    }

    public List<CourseGhostResponse> findTopPercentageGhosts(Long courseId, double percentage) {
        int topNCount = (int) Math.ceil(runningReader.countRunningsInCourse(courseId) * percentage) + 1;
        Sort defaultSort = Sort.by(Sort.Direction.ASC, "runningRecord.averagePace");
        Pageable topNPageable = PageRequest.of(0, topNCount, defaultSort);
        return findPublicGhosts(courseId, topNPageable).getContent();
    }

    @Transactional(readOnly = true)
    public Page<CourseSummaryResponse> findCourseSummariesOfMember(String memberUuid, Pageable pageable) {
        Page<CourseWithMemberDetailsDto> courseDetails = courseReader.findCoursesByMemberUuid(memberUuid, pageable);
        List<CourseSummaryResponse> results = new ArrayList<>();

        for(CourseWithMemberDetailsDto courseDto : courseDetails.getContent()) {
            CourseRunStatisticsDto courseStatistics = runningReader.findCourseRunStatistics(courseDto.getCourseId())
                    .orElse(new CourseRunStatisticsDto());
            courseStatistics = switchTotalRunsCountToUniqueRunnersCount(courseStatistics);
            CourseGhostResponse ghostForUser = getGhostResponse(courseDto.getCourseId(), memberUuid);
            results.add(courseMapper.toCourseSummaryResponse(courseDto, courseStatistics.getUniqueRunnersCount(),
                    courseStatistics.getTotalRunsCount(), courseStatistics.getAvgCompletionTime(),
                    courseStatistics.getAvgFinisherPace(), courseStatistics.getAvgFinisherCadence(), ghostForUser));
        }

        return new PageImpl<>(results, pageable, courseDetails.getTotalElements());
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
        CourseRunStatisticsDto stats = runningReader.findCourseRunStatistics(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        return courseMapper.toCourseStatisticsResponse(stats);
    }

    private CourseGhostResponse getGhostResponse(Long courseId, String viewerUuid) {
        return runningReader.findBestPublicRunForCourse(courseId, viewerUuid)
                .map(runningApiMapper::toGhostResponse)
                .orElse(null);
    }

    private String getTelemetryUrlFromCourse(Course course) {
        if (course.getSource() == CourseSource.OFFICIAL) {
            return course.getCourseDataUrls().getRouteUrl();
        }

        return runningReader.findFirstRunning(course.getId())
                .map(running -> running.getRunningDataUrls().getInterpolatedTelemetryUrl())
                .orElseGet(() -> {
                    log.warn("CourseFacade: No running data found for course id {}", course.getId());
                    return null;
                });
    }


}
