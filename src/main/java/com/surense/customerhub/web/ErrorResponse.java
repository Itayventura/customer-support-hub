package com.surense.customerhub.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.surense.customerhub.common.exception.ErrorCode;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path,
        List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {
    }
}
