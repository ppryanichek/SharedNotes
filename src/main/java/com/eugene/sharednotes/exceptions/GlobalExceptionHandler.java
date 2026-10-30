package com.eugene.sharednotes.exceptions;

import com.eugene.sharednotes.exceptions.dto.ErrorCode;
import com.eugene.sharednotes.exceptions.dto.ErrorResponse;
import com.eugene.sharednotes.exceptions.dto.FieldError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        ErrorResponse error = ErrorResponse.of(status.value(), toErrorCode(status), ex.getMessage());

        return new ResponseEntity<>(error, headers, status);
    }

    private ErrorCode toErrorCode(HttpStatus status) {
        return status.is4xxClientError() ? ErrorCode.BAD_REQUEST : ErrorCode.INTERNAL_ERROR;
    }

    @Override
    public ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        List<FieldError> errors = e.getBindingResult().getFieldErrors()
                .stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), ErrorCode.VALIDATION_FAILED, "Validation failed", errors);

        return new ResponseEntity<>(error, headers, HttpStatus.BAD_REQUEST);
    }

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
    public ResponseEntity<ErrorResponse> handleBadCredentials() {
        HttpStatus httpStatus = HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(httpStatus)
                .body(ErrorResponse.of(httpStatus.value(), ErrorCode.BAD_CREDENTIALS, "Invalid email or password"));
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPassword(InvalidPasswordException e) {
        HttpStatus httpStatus = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(httpStatus)
                .body(ErrorResponse.of(httpStatus.value(), ErrorCode.INVALID_PASSWORD, e.getMessage()));
    }

    @ExceptionHandler(RestoreWindowExpiredException.class)
    public ResponseEntity<ErrorResponse> handleRestoreWindowExpired(RestoreWindowExpiredException e) {
        HttpStatus httpStatus = HttpStatus.GONE;
        return ResponseEntity.status(httpStatus)
                .body(ErrorResponse.of(httpStatus.value(), ErrorCode.RESTORE_WINDOW_EXPIRED, e.getMessage()));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationDenied() {
        HttpStatus httpStatus = HttpStatus.FORBIDDEN;
        return ResponseEntity.status(httpStatus)
                .body(ErrorResponse.of(httpStatus.value(), ErrorCode.ACCESS_DENIED, "Access denied"));
    }

    @ExceptionHandler(NoteNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoteNotFound(NoteNotFoundException e) {
        HttpStatus httpStatus = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(httpStatus)
                .body(ErrorResponse.of(httpStatus.value(), ErrorCode.NOTE_NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(EmptyNoteException.class)
    public ResponseEntity<ErrorResponse> handleEmptyNote(EmptyNoteException e) {
        HttpStatus httpStatus = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(httpStatus)
                .body(ErrorResponse.of(httpStatus.value(), ErrorCode.EMPTY_NOTE, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        log.error("Unhandled exception", e);
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        return  ResponseEntity.status(httpStatus)
                .body(ErrorResponse.of(httpStatus.value(), ErrorCode.INTERNAL_ERROR, "Internal server error"));
    }

}
