package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.dao.CourseSubscriptionRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.domain.CourseSubscription;
import soma.ghostrunner.domain.course.dto.*;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.course.enums.CourseSortType;
import soma.ghostrunner.domain.course.exception.CourseNameNotValidException;
import soma.ghostrunner.domain.course.exception.CourseNotFoundException;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;
    private final CourseRepository courseRepository;
    private final CourseSubscriptionRepository subscriptionRepository;
    private final CourseReadModelRepository readModelRepository;

    public Long save(Course course) {
        return courseRepository.save(course).getId();
    }

    public Course findCourseById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException(ErrorCode.COURSE_NOT_FOUND, id));
    }

    public Course findCourseByIdFetchJoinMember(Long id) {
        return courseRepository.findByIdFetchJoinMember(id)
                .orElseThrow(() -> new CourseNotFoundException(ErrorCode.COURSE_NOT_FOUND, id));
    }

    public List<CoursePreviewDto> findNearbyCourses(Double lat, Double lng, Integer radiusM, CourseSortType sort,
                                                    CourseSearchFilterDto filters) {
        // 코스 검색할 직사각형 반경 계산
        // - 1도 위도 당 111km 가정 (지구 둘레 40,075km / 360도 = 약 111.3km)
        // - 근사치이며, 적도에서 멀어질 수록 경도 거리 오차가 커짐 -> TODO: 추후 Haversine 공식이나 DB 공간 데이터 타입 활용하도록 변경
        LatLngs result = getBoundingBoxLatLngs(lat, lng, radiusM);

        List<Course> courses = courseRepository.findCoursesWithFilters(lat, lng, result.minLat(), result.maxLat(), result.minLng(), result.maxLng(), filters, sort);
        log.info("CourseService::findNearbyCourses() - found {} courses", courses.size());

        return courses.stream()
                .map(courseMapper::toCoursePreviewDto)
                .collect(Collectors.toList());
    }

    public Page<CourseWithMemberDetailsDto> findCoursesByMemberUuid(
            String memberUuid, Pageable pageable) {
        Page<Course> courses = courseRepository.findPublicCoursesFetchJoinMembersByMemberUuidOrderByCreatedAtDesc(memberUuid, pageable);
        return courses.map(c -> courseMapper.toCourseWithMemberDetailsDto(c, c.getMember()));
    }

    @Transactional
    public void deleteCourse(Long courseId, String memberUuid) {
        Course course = findCourseById(courseId);
        course.verifyOwner(memberUuid);
        
        // 리드모델 삭제 (있으면)
        readModelRepository.findByCourseId(courseId)
            .ifPresent(readModelRepository::delete);
        
        courseRepository.delete(course);
    }

    @Transactional
    public void updateCourse(Long courseId, CoursePatchRequest request, String memberUuid) {
        Course course = findCourseById(courseId);
        course.verifyOwner(memberUuid);

        if (request.getName() != null) {
            updateCourseName(course, request.getName());
        }
        if (request.getIsPublic() != null) {
            updateCoursePublicity(course, request.getIsPublic());
            syncReadModelPublicity(courseId, request.getIsPublic()); // 리드모델 동기화
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
        
        boolean currentStatus = course.isPublic();
        
        if (currentStatus == isPublic) {
            log.debug("Course {} is already in desired state: isPublic={}", course.getId(), isPublic);
            return;
        }
        
        // 등록: false -> true
        if (!currentStatus && isPublic) {
            registerCourse(course);
        }
        // 등록 해제: true -> false
        else if (currentStatus && !isPublic) {
            unregisterCourse(course);
        }
    }

    /**
     * 코스 등록 (도메인 + 중간테이블 조율)
     */
    private void registerCourse(Course course) {
        manageSubscriptionForRegister(course);
        course.makePublic();
    }

    /**
     * 코스 등록 해제 (도메인 + 중간테이블 조율)
     */
    private void unregisterCourse(Course course) {
        manageSubscriptionForUnregister(course);
        course.makePrivate();
    }

    /**
     * 등록 시 중간테이블 관리
     * - 중간테이블이 없으면 생성
     * - deleted=true면 restore
     * - deleted=false면 그대로
     */
    private void manageSubscriptionForRegister(Course course) {
        Long courseId = course.getId();
        Long memberId = course.getMember().getId();
        
        Optional<CourseSubscription> existingSubscription = 
                subscriptionRepository.findByCourseIdAndMemberId(courseId, memberId);
        
        if (existingSubscription.isEmpty()) {
            // 새로 등록: 중간테이블 생성
            CourseSubscription newSubscription = CourseSubscription.create(course, course.getMember());
            subscriptionRepository.save(newSubscription);
            log.info("Created new subscription for course={}, member={}", courseId, memberId);
        } else {
            CourseSubscription subscription = existingSubscription.get();
            if (subscription.isDeleted()) {
                // 재등록: restore
                subscription.restore();
                subscriptionRepository.save(subscription);
                log.info("Restored subscription for course={}, member={}", courseId, memberId);
            } else {
                // 이미 활성 상태
                log.debug("Subscription already active for course={}, member={}", courseId, memberId);
            }
        }
    }

    /**
     * 등록 해제 시 중간테이블 관리
     */
    private void manageSubscriptionForUnregister(Course course) {
        Long courseId = course.getId();
        Long memberId = course.getMember().getId();
        
        Optional<CourseSubscription> subscription = 
                subscriptionRepository.findByCourseIdAndMemberId(courseId, memberId);
        
        if (subscription.isPresent()) {
            subscription.get().unregister();
            subscriptionRepository.save(subscription.get());
            log.info("Unregistered subscription for course={}, member={}", courseId, memberId);
        }
    }
    
    /**
     * 리드모델 공개/비공개 동기화
     * 
     * @param courseId 코스 ID
     * @param isPublic true: 공개 (리드모델 생성 또는 공개), false: 비공개 (리드모델 비공개)
     */
    private void syncReadModelPublicity(Long courseId, Boolean isPublic) {
        if (isPublic) {
            // 공개: 리드모델 생성 또는 공개
            CourseReadModel readModel = readModelRepository.findByCourseId(courseId)
                .orElseGet(() -> createReadModelForCourse(courseId));
            readModel.makePublic();
            readModelRepository.save(readModel);
            log.info("Read model made public for course={}", courseId);
        } else {
            // 비공개: 리드모델 비공개 (있으면)
            readModelRepository.findByCourseId(courseId)
                .ifPresent(rm -> {
                    rm.makePrivate();
                    readModelRepository.save(rm);
                    log.info("Read model made private for course={}", courseId);
                });
        }
    }
    
    /**
     * 코스 정보로 리드모델 생성 (저장은 호출자가 담당)
     */
    private CourseReadModel createReadModelForCourse(Long courseId) {
        Course course = findCourseById(courseId);
        CourseReadModel readModel = CourseReadModel.create(course);
        log.info("Created read model for course={}", courseId);
        return readModel;
    }

    /** (lat, lng)을 radiusM로 둘러싼 직사각형의 네 꼭지점 좌표를 반환한다 */
    public static LatLngs getBoundingBoxLatLngs(Double lat, Double lng, double radiusM) {
        double radiusKm = radiusM / 1000d;
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));

        double minLat = lat - latDelta;
        double maxLat = lat + latDelta;
        double minLng = lng - lngDelta;
        double maxLng = lng + lngDelta;

        return new LatLngs(minLat, maxLat, minLng, maxLng);
    }

    public record LatLngs(double minLat, double maxLat, double minLng, double maxLng) {
    }

}
