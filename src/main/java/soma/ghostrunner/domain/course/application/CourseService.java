package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.dao.CourseSubscriptionRepository;
import soma.ghostrunner.domain.course.domain.Course;
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

/**
 * 코스 도메인의 조회·수정·삭제와, 그에 딸린 <b>주인의 구독({@link CourseSubscription}) 조율</b>을 담당한다.
 *
 * <p>책임 경계</p>
 * <ul>
 *   <li><b>코스 본체</b> — 코스 조회(주변/회원별/단건), 코스명·공개 여부 변경, 코스 삭제.</li>
 *   <li><b>주인 구독 조율</b> — 코스 공개 전환은 곧 "주인이 자기 코스를 구독한 상태"를 뜻하므로, 공개/비공개
 *       전환에 맞춰 주인의 구독을 생성·복원·해제한다. (다른 러너의 구독은 코스를 따라 뛸 때
 *       {@code CourseSubscriptionEventListener}가 만든다.)</li>
 *   <li><b>리드모델은 위임</b> — 리드모델 갱신은 직접 하지 않고 전부 {@link CourseReadModelWriter}에 맡긴다.
 *       (코스명 변경 → rename, 공개 전환 → syncPublicity, 코스 삭제 → delete)</li>
 * </ul>
 *
 * 설계 문서: docs/refactoring/course-read-model/04-detailed-design.md §3-2
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;
    private final CourseRepository courseRepository;
    private final CourseSubscriptionRepository subscriptionRepository;
    private final CourseReadModelWriter readModelWriter;

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
                                                    CourseSearchFilterDto filters, Long memberId) {
        // 코스 검색할 직사각형 반경 계산
        // - 1도 위도 당 111km 가정 (지구 둘레 40,075km / 360도 = 약 111.3km)
        // - 근사치이며, 적도에서 멀어질 수록 경도 거리 오차가 커짐 -> TODO: 추후 Haversine 공식이나 DB 공간 데이터 타입 활용하도록 변경
        LatLngs boundingBox = getBoundingBoxLatLngs(lat, lng, radiusM);

        List<Course> nearbyCourses = courseRepository.findCoursesWithFilters(lat, lng,
                boundingBox.minLat(), boundingBox.maxLat(), boundingBox.minLng(), boundingBox.maxLng(),
                filters, sort, memberId);
        log.info("CourseService::findNearbyCourses() - found {} courses", nearbyCourses.size());

        return nearbyCourses.stream()
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

        readModelWriter.delete(courseId);
        courseRepository.delete(course);
    }

    /**
     * 요청에 담긴 필드만 부분 수정한다. (null 인 필드는 건드리지 않는다)
     * 각 수정은 코스 본체를 바꾼 뒤 곧바로 리드모델에 동기화한다.
     */
    @Transactional
    public void updateCourse(Long courseId, CoursePatchRequest request, String memberUuid) {
        Course course = findCourseById(courseId);
        course.verifyOwner(memberUuid);

        String newName = request.getName();
        if (newName != null) {
            updateCourseName(course, newName);
        }

        Boolean newPublicity = request.getIsPublic();
        if (newPublicity != null) {
            updateCoursePublicity(course, newPublicity);
        }

        courseRepository.save(course);
    }

    /**
     * 코스명을 바꾸고 리드모델에도 반영한다. (빈 이름은 허용하지 않는다)
     */
    private void updateCourseName(Course course, String name) {
        if (!StringUtils.hasText(name)) {
            throw new CourseNameNotValidException(ErrorCode.COURSE_NAME_NOT_VALID);
        }
        course.setName(name);
        readModelWriter.rename(course.getId(), name);
    }

    /**
     * 코스 공개 여부를 바꾸고 리드모델에도 반영한다.
     * 이미 원하는 상태라면 등록/해제는 건너뛰되, 리드모델 동기화는 멱등하게 그대로 수행한다.
     */
    private void updateCoursePublicity(Course course, boolean isPublic) {
        if (course.isPublic() != isPublic) {
            if (isPublic) {
                registerCourse(course);
            } else {
                unregisterCourse(course);
            }
        }
        readModelWriter.syncPublicity(course.getId(), isPublic);
    }

    /**
     * 코스 등록 — 주인의 구독을 살린 뒤 코스를 공개로 전환한다. (이름이 없으면 등록 불가)
     */
    private void registerCourse(Course course) {
        if (!StringUtils.hasText(course.getName())) {
            throw new CourseNameNotValidException(ErrorCode.COURSE_NAME_NOT_VALID);
        }
        activateOwnerSubscription(course);
        course.makePublic();
    }

    /**
     * 코스 등록 해제 — 주인의 구독을 해제한 뒤 코스를 비공개로 전환한다.
     */
    private void unregisterCourse(Course course) {
        deactivateOwnerSubscription(course);
        course.makePrivate();
    }

    /**
     * 주인의 구독을 활성 상태로 만든다.
     * - 구독이 없으면 생성 (최초 등록)
     * - 해제된 구독이면 복원 (재등록)
     * - 이미 활성이면 그대로 둔다
     */
    private void activateOwnerSubscription(Course course) {
        Long courseId = course.getId();
        Long ownerId = course.getMember().getId();

        Optional<CourseSubscription> existingSubscription =
                subscriptionRepository.findByCourseIdAndMemberId(courseId, ownerId);

        if (existingSubscription.isEmpty()) {
            subscriptionRepository.save(CourseSubscription.create(course, course.getMember()));
            log.info("Created new subscription for course={}, member={}", courseId, ownerId);
            return;
        }

        CourseSubscription subscription = existingSubscription.get();
        if (subscription.isDeleted()) {
            subscription.restore();
            subscriptionRepository.save(subscription);
            log.info("Restored subscription for course={}, member={}", courseId, ownerId);
        } else {
            log.debug("Subscription already active for course={}, member={}", courseId, ownerId);
        }
    }

    /**
     * 주인의 구독을 해제한다. (구독이 없으면 아무 일도 하지 않는다)
     */
    private void deactivateOwnerSubscription(Course course) {
        Long courseId = course.getId();
        Long ownerId = course.getMember().getId();

        subscriptionRepository.findByCourseIdAndMemberId(courseId, ownerId)
                .ifPresent(subscription -> {
                    subscription.unregister();
                    subscriptionRepository.save(subscription);
                    log.info("Unregistered subscription for course={}, member={}", courseId, ownerId);
                });
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
