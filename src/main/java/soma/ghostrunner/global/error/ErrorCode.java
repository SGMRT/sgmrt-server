package soma.ghostrunner.global.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

@AllArgsConstructor
@Getter
public enum ErrorCode {

    // Ghost-Runner Common Errors
    INVALID_REQUEST_VALUE("G-001", BAD_REQUEST, "잘못된 요청 데이터"),
    INVALID_REQUEST_PARAMETER("G-002", BAD_REQUEST, "잘못된 파라미터"),
    NONE_REQUEST_URI("G-003", BAD_REQUEST, "잘못된 요청 URI"),
    METHOD_NOT_ALLOWED("G-004", HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드"),
    SERVICE_UNAVAILABLE("G-005", HttpStatus.SERVICE_UNAVAILABLE, "서비스 문제 발생"),
    TOO_MANY_REQUESTS("G-006", HttpStatus.TOO_MANY_REQUESTS, "요청 횟수 초과"),
    INVALID_JSON_TYPE("G-007", BAD_REQUEST, "Json 파싱 실패 혹은 올바르지 않은 시간 형식"),
    ENTITY_NOT_FOUND("G-008", NOT_FOUND, "존재하지 않는 엔티티"),
    INVALID_REQUEST_HEADER("G-009", BAD_REQUEST, "잘못되거나 비어있는 헤더"),

    // Auth
    AUTHENTICATION_FAILED("A-001", UNAUTHORIZED, "인증 실패"),
    ACCESS_DENIED("A-002", FORBIDDEN, "허용되지 않은 접근"),
    EXPIRED_TOKEN("A-003", UNAUTHORIZED, "만료된 토큰"),
    INVALID_TOKEN("A-004", UNAUTHORIZED, "유효하지 않은 토큰"),
    EXPIRED_BY_THEFT("A-005", UNAUTHORIZED, "다른 기기에서 로그인하여 세션이 만료되었습니다. 다시 로그인해주세요."),

    // Member
    MEMBER_NOT_FOUND("M-001", NOT_FOUND, "존재하지 않는 회원"),
    MEMBER_ALREADY_EXISTED("M-002", CONFLICT, "이미 존재하는 회원"),
    NICKNAME_ALREADY_EXIST("M-003", CONFLICT, "이미 존재하는 닉네임"),
    TERMS_AGREEMENT_NOT_CHANGED("M-004", CONFLICT, "기존 약관 동의와 동일함"),
    VDOT_NOT_FOUND("M-005", NOT_FOUND, "회원의 VDOT가 존재하지 않음"),
    VDOT_ALREADY_EXIST("M-006", CONFLICT, "이미 VDOT가 존재하는 경우"),

    // Course
    COURSE_NOT_FOUND("C-001", NOT_FOUND, "존재하지 않는 코스"),
    COURSE_NAME_NOT_VALID("C-002", BAD_REQUEST, "올바르지 않은 코스명"),
    COURSE_ALREADY_PUBLIC("C-003", BAD_REQUEST, "코스가 이미 public이므로 수정 불가"),
    COURSE_RUN_NOT_FOUND("C-004", BAD_REQUEST, "해당 코스를 달린 기록이 없음"),

    // Running

    // Notice
    DEPRECATED_NOTICE_TYPE("N-001", BAD_REQUEST, "더 이상 지원되지 않는 공지 타입입니다."),
    NOTICE_ALREADY_ACTIVATED("N-002", BAD_REQUEST, "이미 활성화된 공지입니다. 종료시각을 변경하려면 공지 비활성화 / 공지 수정 API를 활용하세요."),

    // Notification
    EXPO_DEVICE_NOT_REGISTERED("P-001", BAD_REQUEST, "유효하지 않은 Expo Push Token입니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

}
