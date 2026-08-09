package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.course.exception.CourseNameNotValidException;
import soma.ghostrunner.domain.course.exception.CourseNotFoundException;
import soma.ghostrunner.global.error.ErrorCode;

/**
 * 코스 도메인의 <b>DB 쓰기 트랜잭션 경계</b>. (구 {@code CourseService}의 쓰기 절반)
 * 코스 저장·수정·삭제를 담당하고, 그에 딸린 <b>주인 구독은 {@link CourseSubscriptionWriter}에 위임한다.</b>
 *
 * <p>책임 경계</p>
 * <ul>
 *   <li><b>코스 본체</b> — 코스 저장, 코스명·공개 여부 변경, 코스 삭제.</li>
 *   <li><b>주인 구독은 위임</b> — 코스 공개 전환은 곧 "주인이 자기 코스를 구독한 상태"를 뜻한다. 그래서 이 클래스는
 *       공개/비공개 전환에 맞춰 <b>주인 구독을 언제 활성/해제할지만</b> 정하고, 구독 테이블 쓰기 자체는
 *       {@link CourseSubscriptionWriter}가 수행한다. 러너 구독까지 포함해 구독 쓰기는 전부 그쪽이 단일 지점이다.
 *       (설계 문서 reader-writer-layering §4 D2)</li>
 *   <li><b>리드모델은 위임</b> — 리드모델 갱신은 직접 하지 않고 전부 {@link CourseReadModelWriter}에 맡긴다.
 *       (코스명 변경 → rename, 공개 전환 → syncPublicity, 코스 삭제 → delete)</li>
 *   <li><b>조회는 하지 않는다</b> — 읽기 유즈케이스는 {@link CourseReader} 담당. 이 안의 조회는
 *       수정·삭제 대상을 트랜잭션 안에서 확보하기 위한 재조회뿐이다.</li>
 * </ul>
 *
 * 설계 문서: docs/refactoring/course-read-model/core/04-detailed-design.md §3-2
 */
@Service
@RequiredArgsConstructor
public class CourseWriter {

    private final CourseRepository courseRepository;
    private final CourseSubscriptionWriter subscriptionWriter;
    private final CourseReadModelWriter readModelWriter;
    private final CourseMapCacheEvictor mapCacheEvictor;

    /** 새 코스를 저장한다. 러닝 생성 흐름에서는 {@code RunningWriter}의 저장 트랜잭션에 참여한다. */
    @Transactional
    public Long save(Course course) {
        return courseRepository.save(course).getId();
    }

    @Transactional
    public void deleteCourse(Long courseId, String memberUuid) {
        Course course = findCourseById(courseId);
        course.verifyOwner(memberUuid);

        // 삭제 후에는 좌표를 되찾을 수 없으므로, 코스가 아직 살아 있는 지금 좌표를 읽어 커밋 후 이빅트를 예약한다.
        scheduleMapCellEvict(course);

        readModelWriter.delete(courseId);
        courseRepository.delete(course);
    }

    /**
     * 요청에 담긴 필드만 부분 수정한다. (null 인 필드는 건드리지 않는다)
     * 각 수정은 코스 본체를 바꾼 뒤 곧바로 리드모델에 동기화한다.
     *
     * <p><b>이 메서드는 지도에 노출되는 코스 카드(코스명·공개 여부)를 바꾸는 유일한 진입점이다.</b>
     * 그러므로 지도 셀 캐시 이빅트는 개별 수정 메서드가 아니라 여기서 한 번만 요청한다. 이름과 공개 여부가
     * 함께 바뀌어도 캐시 이빅트는 한 번이면 충분하고, 중복 요청은 이빅트 메트릭을 부풀려 관측을 왜곡한다.
     *
     * <p>설계 문서: docs/design/course-cell-bucket-cache-design.md §4(경로 b~d) · D4
     */
    @Transactional
    public void updateCourse(Long courseId, CoursePatchRequest request, String memberUuid) {
        Course course = findCourseById(courseId);
        course.verifyOwner(memberUuid);

        String newName = request.getName();
        Boolean newPublicity = request.getIsPublic();
        boolean courseCardChangeRequested = (newName != null || newPublicity != null);

        if (newName != null) {
            updateCourseName(course, newName);
        }
        if (newPublicity != null) {
            updateCoursePublicity(course, newPublicity);
        }

        courseRepository.save(course);

        if (courseCardChangeRequested) {
            scheduleMapCellEvict(course);
        }
    }

    /** 수정·삭제 대상 코스를 트랜잭션 안에서 조회한다. */
    private Course findCourseById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException(ErrorCode.COURSE_NOT_FOUND, id));
    }

    /**
     * 이 코스가 속한 지도 셀의 캐시를 커밋 후 지우도록 예약한다.
     *
     * <p>좌표는 이미 로드된 {@link Course}에서 읽으므로 추가 쿼리가 없다. 시작점이 없는 코스라면 좌표가
     * {@code null}이고, 그때는 지울 셀을 정할 수 없으므로 이빅터가 건너뛴다.
     */
    private void scheduleMapCellEvict(Course course) {
        Coordinate startCoordinate = course.getStartCoordinate();
        mapCacheEvictor.evictCellAfterCommit(
                course.getId(),
                startCoordinate != null ? startCoordinate.getLatitude() : null,
                startCoordinate != null ? startCoordinate.getLongitude() : null);
    }

    /**
     * 코스명을 바꾸고 리드모델에도 반영한다. (빈 이름은 허용하지 않는다)
     *
     * <p>지도 데이터 변경 이벤트는 발행하지 않는다 — 발행은 호출자인 {@link #updateCourse}가 1회만 담당한다. (D4)
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
     *
     * <p>지도 데이터 변경 이벤트는 발행하지 않는다 — 발행은 호출자인 {@link #updateCourse}가 1회만 담당한다. (D4)
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
     * 코스 등록 — 주인 구독 활성을 위임한 뒤 코스를 공개로 전환한다. (이름이 없으면 등록 불가)
     */
    private void registerCourse(Course course) {
        if (!StringUtils.hasText(course.getName())) {
            throw new CourseNameNotValidException(ErrorCode.COURSE_NAME_NOT_VALID);
        }
        subscriptionWriter.activateOwnerSubscription(course);
        course.makePublic();
    }

    /**
     * 코스 등록 해제 — 주인 구독 해제를 위임한 뒤 코스를 비공개로 전환한다.
     */
    private void unregisterCourse(Course course) {
        subscriptionWriter.deactivateOwnerSubscription(course);
        course.makePrivate();
    }

}
