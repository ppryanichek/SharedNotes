package com.eugene.sharednotes.auth.exceptions;

public class ExpiredRefreshTokenException extends RuntimeException {
    public ExpiredRefreshTokenException() {
        super("Refresh token expired!");
    }
}