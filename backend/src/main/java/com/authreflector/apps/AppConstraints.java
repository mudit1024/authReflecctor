package com.authreflector.apps;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** Bean Validation annotations for URL fields of an app registration (rules in UrlRules). */
public final class AppConstraints {

    private AppConstraints() {
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = RedirectUrisValidator.class)
    public @interface RedirectUris {
        String message() default "Each URL must be https, or http://127.0.0.1 for local development, without #fragment";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = HomeUrlValidator.class)
    public @interface HomeUrl {
        String message() default "Home URL must be an http(s) address";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class RedirectUrisValidator implements ConstraintValidator<RedirectUris, List<String>> {
        @Override
        public boolean isValid(List<String> uris, ConstraintValidatorContext context) {
            return uris == null || uris.stream().allMatch(UrlRules::isAllowedRedirectUri);
        }
    }

    public static class HomeUrlValidator implements ConstraintValidator<HomeUrl, String> {
        @Override
        public boolean isValid(String url, ConstraintValidatorContext context) {
            return url == null || url.isBlank() || UrlRules.isAllowedHomeUrl(url);
        }
    }
}
