package com.surense.customerhub.common.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final ErrorCode code;

    /**
     * Uses the code's default message. Preferred — keeps the wire message consistent for a given code.
     */
    public ApiException(ErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
    }

    /**
     * Overrides the code's default message with a context-specific one
     * (e.g., "User 'alice' not found" for {@link ErrorCode#RESOURCE_NOT_FOUND}).
     */
    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return code.status();
    }
}
