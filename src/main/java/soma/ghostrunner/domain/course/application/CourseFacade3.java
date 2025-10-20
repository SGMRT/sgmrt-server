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
        // 주변 코스의 리드 모델 조회
        Member viewer = memberService.findMemberByUuid(viewerUuid);
        List<CourseReadModel> readModels = findNearCoursesReadModel(lat, lng, radiusM);
        HashMap<Long, CourseReadModel> readModelsHashMap = toHashMap(readModels);

        // 내가 뛴 기록 있는 코스IDs
        Set<Long> viewerRunCourseIds = new HashSet<>(findViewerRunCourses(readModelsHashMap, viewer));
        Map<Long, Boolean> viewerHasRecordsInfoOnCourses = enumerateViewerRunCourses(readModelsHashMap.keySet(), viewerRunCourseIds);

        // 랜덤으로 뽑아서 거리순 정렬
        List<CourseMapResponse3> result = extractRandomCoursesForViewer(readModelsHashMap, viewerHasRecordsInfoOnCourses);
        sortFromCurrentCoordinates(lat, lng, result);
        return result;
    }

    private List<CourseReadModel> findNearCoursesReadModel(Double lat, Double lng, Integer radiusM) {

        double radiusKm = radiusM / 1000d;
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));

        double minLat = lat - latDelta;
        double maxLat = lat + latDelta;
        double minLng = lng - lngDelta;
        double maxLng = lng + lngDelta;
        return courseReadModelRepository.findNearCoursesReadModel(minLat, maxLat, minLng, maxLng);
    }

    private HashMap<Long, CourseReadModel> toHashMap(List<CourseReadModel> readModels) {
        HashMap<Long, CourseReadModel> readModelsMap = new HashMap<>();
        for (CourseReadModel readModel : readModels) {
            Long courseId = readModel.getCourseId();
            readModelsMap.put(courseId, readModel);
        }
        return readModelsMap;
    }

    private List<Long> findViewerRunCourses(HashMap<Long, CourseReadModel> readModelsHashMap, Member viewer) {
        return courseReadModelRepository.findMemberRunningIdsInCourses(readModelsHashMap.keySet(), viewer.getId());
    }

    private Map<Long, Boolean> enumerateViewerRunCourses(Set<Long> nearCoursesId, Set<Long> viewerRunCourseIds) {
        Map<Long, Boolean> result = new HashMap<>();
        for (Long courseId : nearCoursesId) {
            if (viewerRunCourseIds.contains(courseId)) {
                result.put(courseId, true);
            } else {
                result.put(courseId, false);
            }
        }
        return result;
    }

    private List<CourseMapResponse3> extractRandomCoursesForViewer(HashMap<Long, CourseReadModel> readModelsHashMap,
                                                                   Map<Long, Boolean> viewerHasRecordsInfoOnCourses) {

        // 본인, 다른 사람의 코스 나누기
        List<Long> viewerCourseIds = new ArrayList<>();
        List<Long> othersCourseIds = new ArrayList<>();

        for (var courseId : viewerHasRecordsInfoOnCourses.keySet()) {
            if (viewerHasRecordsInfoOnCourses.get(courseId)) {
                viewerCourseIds.add(courseId);
            } else {
                othersCourseIds.add(courseId);
            }
        }

        // 본인의 코스
        Collections.shuffle(viewerCourseIds);
        var viewerCourseRandomIds = viewerCourseIds.stream()
                .limit(Math.max(MAX_RUNNER_PROFILES_PER_COURSE / 2, MAX_RUNNER_PROFILES_PER_COURSE - othersCourseIds.size()))
                .toList();

        // 주변 랜덤 코스
        Collections.shuffle(othersCourseIds);
        var otherCourseRandomIds = othersCourseIds.stream()
                .limit(MAX_RUNNER_PROFILES_PER_COURSE - viewerCourseRandomIds.size())
                .toList();

        // 결과
        return toResult(readModelsHashMap, viewerCourseRandomIds, otherCourseRandomIds);
    }

    private List<CourseMapResponse3> toResult(HashMap<Long, CourseReadModel> readModelsHashMap,
                                              List<Long> viewerCourseRandomIds, List<Long> otherCourseRandomIds) {
        List<CourseMapResponse3> result = new ArrayList<>();
        for (Long courseId : viewerCourseRandomIds) {
            result.add(mapper.toResponse(readModelsHashMap.get(courseId), true));
        }
        for (Long courseId : otherCourseRandomIds) {
            result.add(mapper.toResponse(readModelsHashMap.get(courseId), false));
        }
        return result;
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

    private void sortFromCurrentCoordinates(Double lat, Double lng, List<CourseMapResponse3> result) {
        result.sort(Comparator.comparingDouble(r ->
                distApproxMeters(
                        lat, lng,
                        r.getCourseInfo().getStartLatitude(),
                        r.getCourseInfo().getStartLongitude()
                )
        ));
    }

}
