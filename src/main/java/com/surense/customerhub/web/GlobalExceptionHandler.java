package com.surense.customerhub.web;

import com.surense.customerhub.common.exception.ApiException;
import com.surense.customerhub.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        return build(ex.getCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), request, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(ErrorCode.MALFORMED_REQUEST, ErrorCode.MALFORMED_REQUEST.defaultMessage(), request, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(ErrorCode.AUTH_INVALID_CREDENTIALS, ErrorCode.AUTH_INVALID_CREDENTIALS.defaultMessage(), request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException ex, HttpServletRequest request) {
        return build(ErrorCode.AUTH_MISSING, ErrorCode.AUTH_MISSING.defaultMessage(), request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.defaultMessage(), request, null);
    }

    /**
     * Database constraint violations that made it past pre-checks — typically a race on a
     * unique constraint (two concurrent inserts). Mapped to 409 with a generic message so
     * SQL/schema detail doesn't leak; specifics go to the server log.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation at {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.CONFLICT_GENERIC, ErrorCode.CONFLICT_GENERIC.defaultMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {} {}: ", request.getMethod(), request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), request, null);
    }

    private ResponseEntity<ErrorResponse> build(
            ErrorCode code,
            String message,
            HttpServletRequest request,
            List<ErrorResponse.FieldError> fieldErrors
    ) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                code.status().value(),
                code.status().getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.status(code.status()).body(body);
    }
}
