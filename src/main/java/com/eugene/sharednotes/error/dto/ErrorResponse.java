package com.eugene.sharednotes.error.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        ErrorCode code,
        String message,
        List<FieldError> errors,
        Instant timestamp
) {
    public static ErrorResponse of(int status, ErrorCode code, String message) {
        return new ErrorResponse(status, code, message, null, Instant.now());
    }

    public static ErrorResponse of(int status, ErrorCode code, String message, List<FieldError> errors) {
        return new ErrorResponse(status, code, message, errors, Instant.now());
    }
}
