package com.eugene.sharednotes.exceptions.dto;

public record FieldError(
        String field,
        String message
) {}
