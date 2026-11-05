package itmo.user.adapter.in.web;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return isValidPassword(value);
    }

    public static boolean isValidPassword(String value) {
        return itmo.user.domain.PasswordPolicy.isValid(value);
    }
}
