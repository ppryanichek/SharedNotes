package com.eugene.sharednotes.auth.exceptions;

public class RevokedTokenException extends RuntimeException {
    public RevokedTokenException(String message) {
        super(message);
    }
}
