package com.vijaysinghpuwar.trustkart.common.web;

import com.vijaysinghpuwar.trustkart.common.error.ApiErrorFactory;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Writes {@link com.vijaysinghpuwar.trustkart.common.error.ApiError} bodies from servlet filters, outside MVC. */
@Component
public class JsonErrorWriter {

    private final ApiErrorFactory errors;
    private final ObjectMapper mapper;

    public JsonErrorWriter(ApiErrorFactory errors, ObjectMapper mapper) {
        this.errors = errors;
        this.mapper = mapper;
    }

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), errors.create(code));
    }
}
