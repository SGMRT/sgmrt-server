package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import soma.ghostrunner.domain.course.dto.CourseSearchFilterDto;
import soma.ghostrunner.domain.course.dto.CourseWithCoordinatesDto;
import soma.ghostrunner.domain.course.dto.CourseWithMemberDetailsDto;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.CourseMapper;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.course.dto.response.CourseDetailedResponse;
import soma.ghostrunner.domain.course.exception.CourseAlreadyPublicException;
import soma.ghostrunner.domain.course.exception.CourseNameNotValidException;
import soma.ghostrunner.domain.course.exception.CourseNotFoundException;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;
    private final CourseRepository courseRepository;

    public Long save(
            Course course) {
        return courseRepository.save(course).getId();
    }

    public Course findCourseById(
            Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException(ErrorCode.COURSE_NOT_FOUND, id));
    }

    public List<CourseWithCoordinatesDto> searchCourses(Double lat, Double lng, Integer radiusM,
                                                        CourseSearchFilterDto filters) {
        // 코스 검색할 직사각형 반경 계산
        // - 1도 위도 당 111km 가정 (지구 둘레 40,075km / 360도 = 약 111.3km)
        // - 근사치이며, 적도에서 멀어질 수록 경도 거리 오차가 커짐 -> TODO: 추후 Haversine 공식이나 DB 공간 데이터 타입 활용하도록 변경
        double radiusKm = radiusM / 1000d;
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));

        double minLat = lat - latDelta;
        double maxLat = lat + latDelta;
        double minLng = lng - lngDelta;
        double maxLng = lng + lngDelta;

        List<Course> courses = courseRepository.findCoursesWithFilters(minLat, maxLat, minLng, maxLng, filters);

        return courses.stream()
                .map(courseMapper::toCourseWithCoordinateDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CourseDetailedResponse getCourse(Long courseId) {
      return courseRepository.findCourseDetailedById(courseId)
          .orElseThrow(() -> new CourseNotFoundException(ErrorCode.ENTITY_NOT_FOUND, courseId));
    }

    public Page<CourseWithMemberDetailsDto> findCoursesByMemberUuid(
            String memberUuid, Pageable pageable) {
        Page<Course> courses = courseRepository.findPublicCoursesFetchJoinMembersByMemberUuidOrderByCreatedAtDesc(memberUuid, pageable);
        return courses.map(c -> courseMapper.toCourseWithMemberDetailsDto(c, c.getMember()));
    }

    @Transactional
    public void deleteCourse(Long courseId) {
        Course course = findCourseById(courseId);
        courseRepository.delete(course); // 아마 select 두 번 하게 될 거임
    }

    @Transactional
    public void updateCourse(Long courseId, CoursePatchRequest request) {
        Course course = findCourseById(courseId); // courseId null 체크는 메소드 내에서 이뤄짐

        if (request.getName() != null) {
            updateCourseName(course, request.getName());
        }
        if (request.getIsPublic() != null) {
            updateCoursePublicity(course, request.getIsPublic());
        }
        courseRepository.save(course);
    }

    private void updateCourseName(
            Course course,
            String name) {
        if(course == null) throw new IllegalArgumentException("Course cannot be null");
        if(!StringUtils.hasText(name)) throw new CourseNameNotValidException(ErrorCode.COURSE_NAME_NOT_VALID);
        course.setName(name);
    }

    private void updateCoursePublicity(Course course, Boolean isPublic) {
        if(course == null) throw new IllegalArgumentException("Course cannot be null");
        if(isPublic == null) throw new IllegalArgumentException("IsPublic cannot be null");
        if(course.getIsPublic() == true) throw new CourseAlreadyPublicException(ErrorCode.COURSE_ALREADY_PUBLIC, course.getId());
        course.setIsPublic(isPublic);
    }
}
