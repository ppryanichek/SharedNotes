package com.eugene.sharednotes.user.exceptions;

public class UserAlreadyExistsException  extends RuntimeException{
    private UserAlreadyExistsException(String message) {
        super(message);
    }

    public static UserAlreadyExistsException byUsername(String username) {
        return new UserAlreadyExistsException("User already exists with username: " + username);
    }

    public static UserAlreadyExistsException byEmail(String email) {
        return new UserAlreadyExistsException("User already exists with email: " + email);
    }
}
