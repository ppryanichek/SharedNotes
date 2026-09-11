package com.eugene.sharednotes.exceptions;

public class RestoreWindowExpiredException extends RuntimeException {
    public RestoreWindowExpiredException(String message) {
        super(message);
    }
}
