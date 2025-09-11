package soma.ghostrunner.global.error;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import soma.ghostrunner.global.error.exception.BusinessException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionAdvice {

    // BusinessException
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.error("handleBusinessException", e);
        return createErrorResponse(e.getErrorCode());
    }

    // @Valid, @Validated 핸들링
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("handleMethodArgumentNotValidException", e);
        return createErrorResponse(ErrorCode.INVALID_REQUEST_VALUE, e.getBindingResult());
    }

    // @ModelAttribute 로 바인딩 실패
    @ExceptionHandler(BindException.class)
    protected ResponseEntity<ErrorResponse> handleBindException(BindException e) {
        log.warn("handleBindException", e);
        return createErrorResponse(ErrorCode.INVALID_REQUEST_VALUE, e.getBindingResult());
    }

    // Enum Type 이 일치하지 않아 바인딩 실패
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.error("handleMethodArgumentTypeMismatchException", e);
        return createErrorResponse(ErrorCode.INVALID_REQUEST_VALUE);
    }

    // Json 파싱 에러
    @ExceptionHandler(HttpMessageNotReadableException.class)
    protected ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("handleHttpMessageNotReadableException", e);
        return createErrorResponse(ErrorCode.INVALID_JSON_TYPE);
    }

    // @PathVariable 검증 실패
    @ExceptionHandler(ConstraintViolationException.class)
    protected ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("handleConstraintViolationException", e);
        return createErrorResponse(ErrorCode.INVALID_REQUEST_PARAMETER);
    }

    // @RequestParam 검증 실패
    @ExceptionHandler(MissingServletRequestParameterException.class)
    protected ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("handleMissingServletRequestParameterException", e);
        return createErrorResponse(ErrorCode.INVALID_REQUEST_PARAMETER);
    }

    // 지원하지 않는 URI 요청
    @ExceptionHandler(NoResourceFoundException.class)
    protected ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException e) {
        log.error("handleNoResourceFoundException", e);
        return createErrorResponse(ErrorCode.NONE_REQUEST_URI);
    }

    // 지원하지 않는 HTTP 메서드
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.error("handleHttpRequestMethodNotSupportedException", e);
        return createErrorResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    // 컨트롤러 메소드 파라미터 @Validated 유효성 검사 실패
    @ExceptionHandler(HandlerMethodValidationException.class)
    protected ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        log.error("handleHandlerMethodValidationException", e);
        return createErrorResponse(ErrorCode.INVALID_REQUEST_PARAMETER);
    }

    // 잘못된 데이터 혹은 인자
    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("handleIllegalArgumentException", e);
        return createErrorResponse(ErrorCode.INVALID_REQUEST_PARAMETER);
    }

    // 잘못된 헤더
    @ExceptionHandler(MissingRequestHeaderException.class)
    protected ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(MissingRequestHeaderException e) {
        log.error("handleMissingRequestHeaderException", e);
        return createErrorResponse(ErrorCode.INVALID_REQUEST_HEADER);
    }

    // Multipart 필수 데이터가 없을 때
    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        log.error("handleMissingServletRequestPartException", e);
        return createErrorResponse(ErrorCode.INVALID_REQUEST_PARAMETER);
    }

   // 인증 실패
    @ExceptionHandler(AuthenticationException.class)
    protected ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException e) {
        log.error("handleAuthenticationException", e);
        return createErrorResponse(ErrorCode.AUTHENTICATION_FAILED);
    }

    // 인가 실패
    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
        log.error("handleAccessDeniedException", e);
        return  createErrorResponse(ErrorCode.ACCESS_DENIED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("can't find Handler Entity ", e);
        return createErrorResponse(ErrorCode.SERVICE_UNAVAILABLE);
    }

    private ResponseEntity<ErrorResponse> createErrorResponse(ErrorCode errorCode, BindingResult bindingResult) {
        final ErrorResponse response = ErrorResponse.of(errorCode, bindingResult);
        return new ResponseEntity<>(response, errorCode.getStatus());
    }

    private ResponseEntity<ErrorResponse> createErrorResponse(ErrorCode errorCode) {
        final ErrorResponse response = ErrorResponse.of(errorCode);
        return new ResponseEntity<>(response, errorCode.getStatus());
    }
}
