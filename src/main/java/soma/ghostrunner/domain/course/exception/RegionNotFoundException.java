package soma.ghostrunner.domain.course.exception;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.EntityNotFoundException;

/**
 * 발급된 적 없는 regionId로 지도 조회가 들어온 경우.
 *
 * 설계 문서: docs/refactoring/course-read-model/cache/05-cache-key-design.md §5-2, §6-8
 *
 * <p><b>외부로 노출되지 않는 내부 신호다.</b> {@code CourseReadModelReader}가 던지고
 * {@code CourseFacade}가 잡아 요청 좌표 폴백으로 강등한다 — 홈 화면을 막지 않기 위해서다(설계 §5-1).
 * 예외를 {@code @Cacheable} 프록시 밖으로 흘려보내는 것 자체가 목적이기도 하다:
 * 폴백 결과가 {@code course-map::{regionId}}에 오염 적재되는 것을 막는다.</p>
 */
public class RegionNotFoundException extends EntityNotFoundException {

    public RegionNotFoundException(Long regionId) {
        super(ErrorCode.REGION_NOT_FOUND, regionId);
    }
}
