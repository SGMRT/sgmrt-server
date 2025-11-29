package soma.ghostrunner.global.common.validator.enums;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValueOfEnumValidator implements ConstraintValidator<EnumValid, String> {

    private EnumValid enumValue;

    @Override
    public void initialize(EnumValid constraintAnnotation) {
        this.enumValue = constraintAnnotation;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        boolean result = false;
        Enum<?>[] enumValues = this.enumValue.enumClass().getEnumConstants();
        if (enumValues != null) {
            for (Object enumValue : enumValues) {
                if (value.equals(enumValue.toString())
                        || this.enumValue.ignoreCase() && value.equalsIgnoreCase(enumValue.toString())) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }
}
