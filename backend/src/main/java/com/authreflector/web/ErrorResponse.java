package com.authreflector.web;

import java.time.Instant;
import java.util.List;

// Ported from SplitMate: one error format for every API error, so clients (our React app) parse one shape
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {
    }
}
