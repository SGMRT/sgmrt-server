package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.BoundingBox;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.*;
import soma.ghostrunner.domain.course.enums.CourseSortType;
import soma.ghostrunner.domain.course.exception.CourseNotFoundException;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 코스 도메인의 <b>조회 전용</b> 서비스. (구 {@code CourseService}의 읽기 절반)
 *
 * <p>코스 본체의 저장·수정·삭제와 주인 구독 조율은 전부 {@link CourseWriter}가 담당한다.
 * 이 클래스는 트랜잭션을 열지 않는다 — 쓰기 트랜잭션 안에서 재조회 용도로 불릴 때는
 * 호출자의 트랜잭션에 참여한다. ({@code RunningWriter#saveRun}의 detached 회피 재조회 등)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseQueryService {

    private final CourseMapper courseMapper;
    private final CourseRepository courseRepository;

    public Course findCourseById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException(ErrorCode.COURSE_NOT_FOUND, id));
    }

    public Course findCourseByIdFetchJoinMember(Long id) {
        return courseRepository.findByIdFetchJoinMember(id)
                .orElseThrow(() -> new CourseNotFoundException(ErrorCode.COURSE_NOT_FOUND, id));
    }

    public List<CoursePreviewDto> findNearbyCourses(Double lat, Double lng, Integer radiusM, CourseSortType sort,
                                                    CourseSearchFilterDto filters, Long memberId) {
        BoundingBox boundingBox = BoundingBox.of(lat, lng, radiusM);

        List<Course> nearbyCourses = courseRepository.findCoursesWithFilters(lat, lng,
                boundingBox.minLat(), boundingBox.maxLat(), boundingBox.minLng(), boundingBox.maxLng(),
                filters, sort, memberId);
        log.info("CourseQueryService::findNearbyCourses() - found {} courses", nearbyCourses.size());

        return nearbyCourses.stream()
                .map(courseMapper::toCoursePreviewDto)
                .collect(Collectors.toList());
    }

    public Page<CourseWithMemberDetailsDto> findCoursesByMemberUuid(
            String memberUuid, Pageable pageable) {
        Page<Course> courses = courseRepository.findPublicCoursesFetchJoinMembersByMemberUuidOrderByCreatedAtDesc(memberUuid, pageable);
        return courses.map(c -> courseMapper.toCourseWithMemberDetailsDto(c, c.getMember()));
    }

}
