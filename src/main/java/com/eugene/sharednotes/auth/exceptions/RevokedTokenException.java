package com.eugene.sharednotes.auth.exceptions;

public class RevokedTokenException extends RuntimeException {
    public RevokedTokenException() {
        super("Token reuse detected, all sessions revoked!");
    }
}
