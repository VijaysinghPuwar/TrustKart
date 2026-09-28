package com.vijaysinghpuwar.trustkart.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The only error shape the API returns. It never carries stack traces, SQL, class names or paths.
 * {@code requestId} lets a customer quote a support reference that maps straight to server logs.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        List<FieldError> fieldErrors,
        Map<String, Object> details,
        String requestId) {

    public record FieldError(String field, String message) {}
}
