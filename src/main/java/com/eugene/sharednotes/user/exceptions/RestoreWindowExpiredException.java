package com.eugene.sharednotes.user.exceptions;

public class RestoreWindowExpiredException extends RuntimeException {
    public RestoreWindowExpiredException() {
        super("Account restoration period has expired");
    }
}
