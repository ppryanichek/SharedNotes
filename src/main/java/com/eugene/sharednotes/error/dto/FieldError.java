package com.eugene.sharednotes.error.dto;

public record FieldError(
        String field,
        String message
) {}
