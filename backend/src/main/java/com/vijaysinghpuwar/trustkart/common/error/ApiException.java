package com.vijaysinghpuwar.trustkart.common.error;

import java.util.Map;

/** Base class for expected business failures that map to a specific {@link ErrorCode}. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> details;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage(), Map.of());
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = Map.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
