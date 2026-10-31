package com.eugene.sharednotes.user.exceptions;

public class RestoreWindowExpiredException extends RuntimeException {
    public RestoreWindowExpiredException(String message) {
        super(message);
    }
}
