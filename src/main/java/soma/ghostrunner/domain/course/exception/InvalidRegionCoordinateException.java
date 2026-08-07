package soma.ghostrunner.domain.course.exception;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.InvalidValueException;

/**
 * 서비스 영역(한국) 밖의 좌표로 지역을 <b>신규 등록</b>하려는 경우.
 *
 * 설계 문서: docs/refactoring/course-read-model/cache/05-cache-key-design.md §3-2, §5-1
 * 지역의 대표좌표는 최초 등록 후 불변이고(캐시 값의 결정성 근거), 오염된 행의 복구 수단은 운영자의 수동 삭제뿐이다.
 * 따라서 이상 좌표는 적재 시점에 막는다. 이미 등록된 지역의 조회 경로는 좌표와 무관하게 통과한다.
 *
 * <p>예외 메시지·로그에 좌표값을 남기지 않는다 (개인위치정보).</p>
 */
public class InvalidRegionCoordinateException extends InvalidValueException {

    public InvalidRegionCoordinateException() {
        super(ErrorCode.REGION_COORDINATE_NOT_VALID);
    }

    public InvalidRegionCoordinateException(String message) {
        super(ErrorCode.REGION_COORDINATE_NOT_VALID, message);
    }
}
