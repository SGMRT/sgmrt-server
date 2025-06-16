package soma.ghostrunner.global.common.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

@AllArgsConstructor
@Getter
public enum ErrorCode {

    // Common
    INVALID_REQUEST_VALUE("C-001",  BAD_REQUEST, "잘못된 요청 데이터"),
    MISSING_REQUESTED_DATA("C-002",  BAD_REQUEST, "필수 파라미터 누락"),
    NONE_REQUEST_URI("C-003",  BAD_REQUEST, "잘못된 요청 URI"),
    METHOD_NOT_ALLOWED("C-004",  HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드"),
    SERVICE_UNAVAILABLE("C-005",  HttpStatus.SERVICE_UNAVAILABLE, "서비스 문제 발생"),
    TOO_MANY_REQUESTS("C-006",  HttpStatus.TOO_MANY_REQUESTS, "요청 횟수 초과"),

    // Member
    MEMBER_NOT_FOUND("M-001", NOT_FOUND, "존재하지 않는 회원"),
    MEMBER_ALREADY_EXISTED("M-002", CONFLICT, "이미 존재하는 회원"),
    ;

    private final String code;
    private final HttpStatus status;
    private final String message;
}
