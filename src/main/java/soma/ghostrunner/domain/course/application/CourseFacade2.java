package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.dto.BestDurationInCourseDto;
import soma.ghostrunner.domain.course.dto.*;
import soma.ghostrunner.domain.course.dto.response.CourseMapResponse2;
import soma.ghostrunner.domain.course.enums.CourseSortType;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.dto.MemberMetaInfoDto;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseFacade2 {

    private final CourseRepository courseRepository;
    private final CourseMapper mapper;

    private final MemberService memberService;

    private static final int MAX_RUNNER_PROFILES_PER_COURSE = 10;

    @Transactional(readOnly = true)
    public List<CourseMapResponse2> findCoursesByPosition(Double lat, Double lng, Integer radiusM, CourseSortType sort,
                                                          CourseSearchFilterDto filters, String viewerUuid) {

        // 멤버, 주변 코스 조회
        Member member = memberService.findMemberByUuid(viewerUuid);
        List<BestDurationInCourseDto> bestDurationInCourses = findNearCoursesAndBestDurationPerRunners(lat, lng, radiusM);
        HashMap<Long, List<BestDurationInCourseDto>> bestDurationInCoursesMap = toHashMap(bestDurationInCourses);

        // 코스 내 메타정보 가공
        HashMap<Long, CoursePreviewDto2> coursePreviewMap = new HashMap<>();
        for (Long courseId : bestDurationInCoursesMap.keySet()) {
            CoursePreviewDto2 previewDto = CoursePreviewDto2.of(bestDurationInCoursesMap.get(courseId), member.getId());
            coursePreviewMap.put(courseId, previewDto);
        }

        // 랜덤 뽑기
        coursePreviewMap = extractRandomCoursesForViewer(coursePreviewMap);

        // 유니크한 코스, 멤버 ID
        Set<Long> uniqueCourseIds = coursePreviewMap.keySet();
        Set<Long> uniqueMemberIds = extractUniqueMemberIds(uniqueCourseIds, coursePreviewMap);

        // DB에서 조회
        List<CourseMetaInfoDto> courseMetaInfoDtos = courseRepository.findCourseMetaInfoByCourseId(uniqueCourseIds);
        Map<Long, CourseMetaInfoDto> courseMetaInfoMap = toCourseMetaInfoMap(courseMetaInfoDtos);

        List<MemberMetaInfoDto> memberMetaInfoDtos = memberService.findMemberMetaInfo(uniqueMemberIds);
        Map<Long, MemberMetaInfoDto> memberMetaInfoMap = toMemberMetaInfoMap(memberMetaInfoDtos);

        // 변환
        return mapper.toResponse(coursePreviewMap, courseMetaInfoMap, memberMetaInfoMap);
    }

    private HashMap<Long, MemberMetaInfoDto> toMemberMetaInfoMap(List<MemberMetaInfoDto> memberMetaInfoDtos) {
        return memberMetaInfoDtos.stream()
                .collect(Collectors.toMap(
                        MemberMetaInfoDto::getMemberId,
                        dto -> dto,
                        (existing, replacement) -> existing,
                        HashMap::new
                ));
    }

    private Set<Long> extractUniqueMemberIds(Set<Long> uniqueCourseIds, HashMap<Long, CoursePreviewDto2> coursePreviewMap) {
        Set<Long> uniqueMemberIds = new HashSet<>();
        for (Long courseId : uniqueCourseIds) {
            CoursePreviewDto2 dto = coursePreviewMap.get(courseId);
            List<Long> top4RunnersId = dto.getTop4RunnerIds();
            uniqueMemberIds.addAll(top4RunnersId);
        }
        return uniqueMemberIds;
    }

    private @NotNull HashMap<Long, CourseMetaInfoDto> toCourseMetaInfoMap(List<CourseMetaInfoDto> courseMetaInfoDtos) {
        return courseMetaInfoDtos.stream()
                .collect(Collectors.toMap(
                        CourseMetaInfoDto::getCourseId,
                        dto -> dto,
                        (existing, replacement) -> existing,
                        HashMap::new
                ));
    }

    private List<BestDurationInCourseDto> findNearCoursesAndBestDurationPerRunners(Double lat, Double lng, Integer radiusM) {

        double radiusKm = radiusM / 1000d;
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));

        double minLat = lat - latDelta;
        double maxLat = lat + latDelta;
        double minLng = lng - lngDelta;
        double maxLng = lng + lngDelta;

        List<BestDurationProjection> projections = courseRepository.findBestDurations(minLat, maxLat, minLng, maxLng);
        return BestDurationInCourseDto.toList(projections);
    }

    private HashMap<Long, List<BestDurationInCourseDto>> toHashMap(List<BestDurationInCourseDto> bestDurationInCourseDtos) {
        HashMap<Long, List<BestDurationInCourseDto>> bestDurationInCourseDtoHashMap = new HashMap<>();
        for (BestDurationInCourseDto bestDurationInCourseDto : bestDurationInCourseDtos) {
            Long courseId = bestDurationInCourseDto.getCourseId();
            bestDurationInCourseDtoHashMap.computeIfAbsent(courseId, k -> new ArrayList<>())
                    .add(bestDurationInCourseDto);
        }
        return bestDurationInCourseDtoHashMap;
    }

    private HashMap<Long, CoursePreviewDto2> extractRandomCoursesForViewer(HashMap<Long, CoursePreviewDto2> coursePreviewMap) {

        // 본인, 다른 사람의 코스 나누기
        var usersCoursesMap = new HashMap<Long, CoursePreviewDto2>();
        var othersCoursesMap = new HashMap<Long, CoursePreviewDto2>();

        Set<Long> courseIds = coursePreviewMap.keySet();
        for (var courseId : courseIds) {
            CoursePreviewDto2 previewDto = coursePreviewMap.get(courseId);
            if (previewDto.isHasMyRecord()) {
                usersCoursesMap.put(courseId, previewDto);
            } else {
                othersCoursesMap.put(courseId, previewDto);
            }
        }

        // 본인의 코스
        var userCourseIndices = new ArrayList<>(usersCoursesMap.keySet());
        Collections.shuffle(userCourseIndices);
        var userCourseRandomIndices = userCourseIndices.stream()
                .limit(Math.max(MAX_RUNNER_PROFILES_PER_COURSE / 2, MAX_RUNNER_PROFILES_PER_COURSE - othersCoursesMap.size()))
                .toList();

        // 주변 랜덤 코스
        var otherCourseIndices = new ArrayList<>(othersCoursesMap.keySet());
        Collections.shuffle(otherCourseIndices);
        var otherCourseRandomIndices = otherCourseIndices.stream()
                .limit(MAX_RUNNER_PROFILES_PER_COURSE - userCourseIndices.size())
                .toList();

        // 인덱스 기존 순서대로 정렬 후 dto로 매핑하여 반환
        var finalIndices = new ArrayList<Long>();
        finalIndices.addAll(userCourseRandomIndices);
        finalIndices.addAll(otherCourseRandomIndices);
        Collections.sort(finalIndices);

        // 리턴
        HashMap<Long, CoursePreviewDto2> result = new HashMap<>();
        for (Long courseId : finalIndices) {
            result.put(courseId, coursePreviewMap.get(courseId));
        }
        return result;
    }

}
