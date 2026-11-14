package com.authreflector.web;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

// Writes errors from security filters (which run before controllers) in the same ErrorResponse format
// that GlobalExceptionHandler uses, so the React app parses one error shape everywhere
@Component
public class JsonErrorWriter {

    private final JsonMapper jsonMapper;
    private final Clock clock;

    public JsonErrorWriter(JsonMapper jsonMapper, Clock clock) {
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        ErrorResponse body = new ErrorResponse(
                Instant.now(clock), status.value(), status.getReasonPhrase(), message, request.getRequestURI(), List.of());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
