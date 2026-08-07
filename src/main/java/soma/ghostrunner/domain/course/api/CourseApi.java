package soma.ghostrunner.domain.course.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.course.application.CourseFacade;
import soma.ghostrunner.domain.course.dto.CourseSearchFilterDto;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.course.dto.response.*;
import soma.ghostrunner.domain.course.enums.CourseSortType;
import soma.ghostrunner.global.security.jwt.JwtUserDetails;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class CourseApi {

    private final CourseFacade courseFacade;

    /**
     * 주변 코스 지도 조회.
     *
     * <p>{@code regionId}는 구 지역 캐시 설계의 잔재이자 <b>서버가 사용하지 않는</b> 파라미터다.
     * 배포된 앱이 계속 실어 보내므로 하위호환을 위해 수용만 하고 조회에는 반영하지 않는다 —
     * 첨부 여부와 무관하게 결과는 오직 요청 좌표({@code lat}·{@code lng})와 {@code radiusM}으로
     * 결정된다 (설계 문서: docs/design/course-cell-bucket-cache-design.md §3-10).</p>
     */
    @GetMapping("/courses")
    public List<CourseMapResponse> getCoursesByPosition(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(required = false, defaultValue = "2000") @Max(value = 20000) Integer radiusM,
            @RequestParam(required = false, defaultValue = "DISTANCE") CourseSortType sort,
            @RequestParam(required = false) String ownerUuid,
            @RequestParam(required = false) Integer minDistanceM,
            @RequestParam(required = false) Integer maxDistanceM,
            @RequestParam(required = false) Integer minElevationM,
            @RequestParam(required = false) Integer maxElevationM,
            @RequestParam(required = false) Long regionId,
            @AuthenticationPrincipal JwtUserDetails userDetails) {
        return courseFacade.findCoursesByPosition(lat, lng, radiusM, sort,
                CourseSearchFilterDto.of(minDistanceM, maxDistanceM, minElevationM, maxElevationM, ownerUuid),
                regionId, userDetails.getUserId());
    }

    @GetMapping("/courses/{courseId}")
    public CourseDetailedResponse getCourse(
            @PathVariable("courseId") Long courseId,
            @AuthenticationPrincipal JwtUserDetails userDetails) {
        return courseFacade.findCourse(courseId, userDetails.getUserId());
    }

    @PatchMapping("/courses/{courseId}")
    public void updateCourse(
            @PathVariable("courseId") Long courseId,
            @RequestBody CoursePatchRequest request,
            @AuthenticationPrincipal JwtUserDetails userDetails) {
        courseFacade.updateCourse(courseId, request, userDetails.getUserId());
    }

    @DeleteMapping("/courses/{courseId}")
    public void deleteCourse(
            @PathVariable("courseId") Long courseId,
            @AuthenticationPrincipal JwtUserDetails userDetails) {
        courseFacade.deleteCourse(courseId, userDetails.getUserId());
    }

    @GetMapping("/courses/{courseId}/ghosts")
    public PagedModel<CourseGhostResponse> getGhosts(
            @PathVariable("courseId") Long courseId,
            @PageableDefault(sort = "runningRecord.averagePace") Pageable pageable) {
        return new PagedModel<>(courseFacade.findPublicGhosts(courseId, pageable));
        // max 페이지 크기 설정
    }

    @GetMapping("/courses/{courseId}/ranking")
    public CourseRankingResponse getCourseRanking(
            @PathVariable("courseId") Long courseId,
            @RequestParam String memberUuid) {
        return courseFacade.findCourseRankingDetail(courseId, memberUuid);
    }

    @GetMapping("/courses/{courseId}/top-ranking")
    public List<CourseGhostResponse> getTopRankingGhosts(
            @PathVariable("courseId") Long courseId,
            @RequestParam(required = false, defaultValue = "10") @Min(value = 1) @Max(value = 50) Integer count) {
        return courseFacade.findTopRankingGhosts(courseId, count);
    }

    @GetMapping("/courses/{courseId}/top-percentage")
    public List<CourseGhostResponse> getTopPercentageGhosts(
            @PathVariable("courseId") Long courseId,
            @RequestParam @Min(value = 0) @Max(value = 1) Double percentage) {
        return courseFacade.findTopPercentageGhosts(courseId, percentage);
    }

    @GetMapping("/courses/{courseId}/statistics")
    public CourseStatisticsResponse getCourseStatistics(
            @PathVariable("courseId") Long courseId) {
        return courseFacade.findCourseStatistics(courseId);
    }

    @PreAuthorize("@authService.isOwner(#memberUuid, #userDetails)")
    @GetMapping("/members/{memberUuid}/courses")
    public PagedModel<CourseSummaryResponse> getMemberCourses(
            @PathVariable("memberUuid") String memberUuid,
            @PageableDefault Pageable pageable,
            @AuthenticationPrincipal JwtUserDetails userDetails) {
        return new PagedModel<>(courseFacade.findCourseSummariesOfMember(memberUuid, pageable));
    }

}
