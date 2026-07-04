package com.surense.customerhub.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Machine-readable identifier for every failure this API can return.
 * Each entry owns both its HTTP status and its default human-readable message so callers
 * that don't need contextual detail get a canonical message automatically.
 *
 * <p>Default messages are factual, user-friendly English suitable as a fallback if the
 * client renders them directly. Machine clients should branch on the enum name
 * ({@link #name()}) rather than parse the message.
 */
public enum ErrorCode {

    // ---- 400 ----
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Some fields were invalid. Please review and try again."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be understood."),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "A request parameter has an invalid value."),
    PASSWORD_CURRENT_INCORRECT(HttpStatus.BAD_REQUEST, "Current password is incorrect."),
    PASSWORD_SAME_AS_CURRENT(HttpStatus.BAD_REQUEST, "New password must be different from the current one."),

    // ---- 401 ----
    AUTH_MISSING(HttpStatus.UNAUTHORIZED, "Please sign in to continue."),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid username or password."),

    // ---- 403 ----
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),

    // ---- 404 ----
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested item could not be found."),

    // ---- 405 ----
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "This method is not allowed on this resource."),

    // ---- 409 ----
    CONFLICT_DUPLICATE_USERNAME(HttpStatus.CONFLICT, "This username is already taken."),
    CONFLICT_DUPLICATE_EMAIL(HttpStatus.CONFLICT, "This email is already registered."),
    CONFLICT_GENERIC(HttpStatus.CONFLICT, "This action conflicts with existing data."),

    // ---- 500 ----
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.");

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
