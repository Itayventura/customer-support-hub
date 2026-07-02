package com.surense.customerhub.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Machine-readable identifier for every failure this API can return.
 * Each entry owns both its HTTP status and its default human-readable message so callers
 * that don't need contextual detail get a canonical message automatically.
 *
 * <p>Serialized to clients in the {@code code} field of the error response so they can
 * branch on error type without parsing the {@code message}.
 */
public enum ErrorCode {

    // ---- 400 ----
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request body"),
    PASSWORD_CURRENT_INCORRECT(HttpStatus.BAD_REQUEST, "Current password is incorrect"),
    PASSWORD_SAME_AS_CURRENT(HttpStatus.BAD_REQUEST, "New password must be different from the current one"),

    // ---- 401 ----
    AUTH_MISSING(HttpStatus.UNAUTHORIZED, "Authentication required"),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid username or password"),

    // ---- 403 ----
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),

    // ---- 404 ----
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),

    // ---- 409 ----
    CONFLICT_DUPLICATE_USERNAME(HttpStatus.CONFLICT, "Username already taken"),
    CONFLICT_DUPLICATE_EMAIL(HttpStatus.CONFLICT, "Email already registered"),
    CONFLICT_GENERIC(HttpStatus.CONFLICT, "Conflict with existing resource"),

    // ---- 500 ----
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
