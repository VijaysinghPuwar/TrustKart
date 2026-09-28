package com.vijaysinghpuwar.trustkart.common.error;

import com.vijaysinghpuwar.trustkart.common.web.RequestIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorFactory {

    private final Clock clock;

    public ApiErrorFactory(Clock clock) {
        this.clock = clock;
    }

    public ApiError create(ErrorCode code) {
        return create(code, code.defaultMessage(), List.of(), Map.of());
    }

    public ApiError create(ErrorCode code, String message, List<ApiError.FieldError> fieldErrors, Map<String, Object> details) {
        return new ApiError(
                Instant.now(clock),
                code.status().value(),
                code.name(),
                message,
                fieldErrors,
                details,
                MDC.get(RequestIdFilter.MDC_KEY));
    }
}
