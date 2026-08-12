package com.eugene.sharednotes.exceptions.dto;

public enum ErrorCode {
    USER_NOT_FOUND,
    USER_ALREADY_EXISTS,
    TOKEN_REVOKED,
    TOKEN_EXPIRED,
    UNAUTHORIZED,
    BAD_CREDENTIALS,
    RESOURCE_NOT_FOUND,
    VALIDATION_FAILED,
    INTERNAL_ERROR
}
