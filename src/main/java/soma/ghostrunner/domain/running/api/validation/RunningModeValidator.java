package soma.ghostrunner.domain.running.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

public class RunningModeValidator implements ConstraintValidator<ValidateRunningMode, Object> {

    private String modeField;
    private String ghostRunningId;

    @Override
    public void initialize(ValidateRunningMode constraintAnnotation) {
        this.modeField = constraintAnnotation.modeField();
        this.ghostRunningId = constraintAnnotation.ghostRunningId();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {

        String modeValue = (String) new BeanWrapperImpl(value).getPropertyValue(modeField);
        Object ghostRunningIdValue = new BeanWrapperImpl(value).getPropertyValue(ghostRunningId);

        // mode 필드 자체가 null 이거나 비어있으면 통과
        if (modeValue == null || modeValue.trim().isEmpty()) {
            return true;
        }

        // 검증
        boolean isValid = validateRunningModes(modeValue, ghostRunningIdValue);

        // (검증 실패 시, 오류 메시지를 ghostRunningId 필드에 할당
        if (!isValid) {
            context.disableDefaultConstraintViolation();     // 기본 메시지 비활성화
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(ghostRunningId)        // 오류를 targetField(ghostRunningId)에 연결
                    .addConstraintViolation();
        }

        return isValid;
    }

    private boolean validateRunningModes(String modeValue, Object ghostRunningIdValue) {
        if ("SOLO".equalsIgnoreCase(modeValue)) {
            return ghostRunningIdValue == null;
        } else if ("GHOST".equalsIgnoreCase(modeValue)) {
            return ghostRunningIdValue != null;
        }
        return true;
    }
}
