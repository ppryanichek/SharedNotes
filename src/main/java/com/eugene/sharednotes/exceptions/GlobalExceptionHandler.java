package com.eugene.sharednotes.exceptions;

import com.eugene.sharednotes.exceptions.dto.ErrorCode;
import com.eugene.sharednotes.exceptions.dto.ErrorResponse;
import com.eugene.sharednotes.exceptions.dto.FieldError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException e) {
        HttpStatus httpStatus = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(httpStatus)
                .body(ErrorResponse.of(httpStatus.value(), ErrorCode.USER_NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException e) {
        HttpStatus httpStatus = HttpStatus.CONFLICT;
        return ResponseEntity.status(httpStatus)
                .body(ErrorResponse.of(httpStatus.value(), ErrorCode.USER_ALREADY_EXISTS, e.getMessage()));
    }

    @ExceptionHandler(RevokedTokenException.class)
    public ResponseEntity<ErrorResponse> handleRevokedToken(RevokedTokenException e) {
        HttpStatus httpStatus = HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(httpStatus)
                .body(ErrorResponse.of(httpStatus.value(), ErrorCode.TOKEN_REVOKED, e.getMessage()));
    }

    @ExceptionHandler(ExpiredRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleExpiredRefresh(ExpiredRefreshTokenException e) {
        HttpStatus httpStatus = HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(httpStatus)
                .body(ErrorResponse.of(httpStatus.value(), ErrorCode.TOKEN_EXPIRED, e.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException e) {
        HttpStatus httpStatus = HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(httpStatus)
                .body(ErrorResponse.of(httpStatus.value(), ErrorCode.BAD_CREDENTIALS, "Invalid email or password"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        HttpStatus httpStatus = HttpStatus.BAD_REQUEST;

        List<FieldError> errors = e.getBindingResult().getFieldErrors()
                .stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        return ResponseEntity.status(httpStatus)
                .body(ErrorResponse.of(httpStatus.value(), ErrorCode.VALIDATION_FAILED , "Validation failed", errors));
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPassword(InvalidPasswordException e) {
        HttpStatus httpStatus = HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(httpStatus)
                .body(ErrorResponse.of(httpStatus.value(), ErrorCode.INVALID_PASSWORD, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        log.error("Unhandled exception", e);
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        return  ResponseEntity.status(httpStatus)
                .body(ErrorResponse.of(httpStatus.value(), ErrorCode.INTERNAL_ERROR, "Internal server error"));
    }
}
