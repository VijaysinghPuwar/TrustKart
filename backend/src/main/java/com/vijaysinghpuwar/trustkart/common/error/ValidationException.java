package com.vijaysinghpuwar.trustkart.common.error;

import java.util.List;

/** Business-rule validation failure tied to specific request fields (e.g. password policy). */
public class ValidationException extends ApiException {

    private final transient List<ApiError.FieldError> fieldErrors;

    public ValidationException(List<ApiError.FieldError> fieldErrors) {
        super(ErrorCode.VALIDATION_ERROR);
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public ValidationException(String field, String message) {
        this(List.of(new ApiError.FieldError(field, message)));
    }

    public List<ApiError.FieldError> fieldErrors() {
        return fieldErrors;
    }
}
