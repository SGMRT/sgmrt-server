package soma.ghostrunner.domain.running.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

public class NoPauseForPublicValidator implements ConstraintValidator<NoPauseForPublic, Object> {

    private String hasPaused;
    private String isPublic;

    @Override
    public void initialize(NoPauseForPublic constraintAnnotation) {
        this.hasPaused = constraintAnnotation.hasPaused();
        this.isPublic = constraintAnnotation.isPublic();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {

        Boolean hasPausedValue = (Boolean) new BeanWrapperImpl(value).getPropertyValue(hasPaused);
        Boolean isPublicValue = (Boolean) new BeanWrapperImpl(value).getPropertyValue(isPublic);

        if (hasPausedValue == null || isPublicValue == null) {
            return true;
        }

        boolean isValid = !hasPausedValue || !isPublicValue;

        if (!isValid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(hasPaused)
                    .addConstraintViolation();
        }

        return isValid;
    }
}
