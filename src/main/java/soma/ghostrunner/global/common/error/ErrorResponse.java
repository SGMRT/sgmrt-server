package soma.ghostrunner.global.common.error;

import lombok.*;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
public class ErrorResponse {

    private String code;
    private String message;
    private List<FieldErrorInfo> fieldErrorInfos;

    @Builder(access = AccessLevel.PRIVATE)
    private ErrorResponse(String code, String message, List<FieldErrorInfo> fieldErrorInfos) {
        this.code = code;
        this.message = message;
        this.fieldErrorInfos = fieldErrorInfos;
    }

    public static ErrorResponse of(ErrorCode errorCode, BindingResult bindingResult) {
        List<FieldErrorInfo> fieldErrorInfos = Optional.ofNullable(bindingResult)
                .map(FieldErrorInfo::from)
                .orElseGet(Collections::emptyList);

        return ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .fieldErrorInfos(fieldErrorInfos)
                .build();
    }

    public static ErrorResponse from(ErrorCode errorCode) {
        return ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .fieldErrorInfos(Collections.emptyList())
                .build();
    }

    @Getter
    public static class FieldErrorInfo {

        private String field;
        private String value;
        private String reason;

        @Builder(access = AccessLevel.PRIVATE)
        private FieldErrorInfo(String field, String value, String reason) {
            this.field = field;
            this.value = value;
            this.reason = reason;
        }

        private static List<FieldErrorInfo> from(BindingResult bindingResult) {
            List<FieldError> fieldErrors = bindingResult.getFieldErrors();
            return fieldErrors.stream().
                    map(error -> FieldErrorInfo.builder()
                            .field(error.getField())
                            .value((error.getRejectedValue() == null) ? null : error.getRejectedValue().toString())
                            .reason(error.getDefaultMessage())
                            .build())
                    .collect(Collectors.toList());
        }
    }
}
