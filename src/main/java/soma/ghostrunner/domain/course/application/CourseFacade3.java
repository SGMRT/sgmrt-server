package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.dto.*;
import soma.ghostrunner.domain.course.dto.response.CourseMapResponse3;
import soma.ghostrunner.domain.course.enums.CourseSortType;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseFacade3 {

    private static final double EARTH_RADIUS_M = 6371000.0;

    private final CourseReadModelRepository courseReadModelRepository;
    private final CourseMapper mapper;

    private final MemberService memberService;

    private static final int MAX_RUNNER_PROFILES_PER_COURSE = 10;

    @Transactional(readOnly = true)
    public List<CourseMapResponse3> findCoursesByPosition(Double lat, Double lng, Integer radiusM, CourseSortType sort,
                                                          CourseSearchFilterDto filters, String viewerUuid) {

        Member viewer = memberService.findMemberByUuid(viewerUuid);

        List<Long> nearCourseIds = findNearCourseIds(lat, lng, radiusM);
        if (nearCourseIds.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> viewerRunCourseIds = new HashSet<>(findViewerRunCourses(nearCourseIds, viewer));

        List<Long> readModelCourseIds = extractRandomCoursesForViewer(viewerRunCourseIds, nearCourseIds);
        List<CourseReadModel> readModels = courseReadModelRepository.findByCourseIdIn(readModelCourseIds);

        List<CourseMapResponse3> res = mapper.toResponse(readModels, viewerRunCourseIds);
        sortFromCurrentCoordinates(lat, lng, res);
        return res;
    }

    private List<Long> findNearCourseIds(Double lat, Double lng, Integer radiusM) {

        double radiusKm = radiusM / 1000d;
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));

        double minLat = lat - latDelta;
        double maxLat = lat + latDelta;
        double minLng = lng - lngDelta;
        double maxLng = lng + lngDelta;
        return courseReadModelRepository.findNearCourseIds(minLat, maxLat, minLng, maxLng);
    }

    private List<Long> findViewerRunCourses(List<Long> nearCourseIds, Member viewer) {
        return courseReadModelRepository.findMemberRunningIdsInCourses(nearCourseIds, viewer.getId());
    }

    private List<Long> extractRandomCoursesForViewer(Set<Long> viewerRunCourseIds, List<Long> nearCourseIds) {

        // 본인, 다른 사람의 코스 나누기
        List<Long> viewerCourseIds = new ArrayList<>();
        List<Long> othersCourseIds = new ArrayList<>();

        for (var courseId : nearCourseIds) {
            if (viewerRunCourseIds.contains(courseId)) {
                viewerCourseIds.add(courseId);
            } else {
                othersCourseIds.add(courseId);
            }
        }

        // 결과
        List<Long> resultCourseIds = new ArrayList<>();

        // 본인의 코스
        Collections.shuffle(viewerCourseIds);
        var viewerCourseRandomIds = viewerCourseIds.stream()
                .limit(Math.max(MAX_RUNNER_PROFILES_PER_COURSE / 2, MAX_RUNNER_PROFILES_PER_COURSE - othersCourseIds.size()))
                .toList();
        resultCourseIds.addAll(viewerCourseRandomIds);

        // 주변 랜덤 코스
        Collections.shuffle(othersCourseIds);
        var otherCourseRandomIds = othersCourseIds.stream()
                .limit(MAX_RUNNER_PROFILES_PER_COURSE - viewerCourseRandomIds.size())
                .toList();
        resultCourseIds.addAll(otherCourseRandomIds);

        // 결과
        return resultCourseIds;
    }

    private void sortFromCurrentCoordinates(Double lat, Double lng, List<CourseMapResponse3> result) {
        result.sort(Comparator.comparingDouble(r ->
                distApproxMeters(
                        lat, lng,
                        r.getCourseInfo().getStartLatitude(),
                        r.getCourseInfo().getStartLongitude()
                )
        ));
    }

    private double distApproxMeters(double lat1, double lng1, double lat2, double lng2) {
        double φ1 = Math.toRadians(lat1);
        double φ2 = Math.toRadians(lat2);
        double λ1 = Math.toRadians(lng1);
        double λ2 = Math.toRadians(lng2);
        double x = (λ2 - λ1) * Math.cos((φ1 + φ2) / 2.0);
        double y = (φ2 - φ1);
        return Math.sqrt(x * x + y * y) * EARTH_RADIUS_M;
    }

}
