package com.vijaysinghpuwar.trustkart.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns every request a correlation ID, exposes it as {@code X-Request-ID}, puts it in the logging MDC
 * and writes one access-log line per request. A caller-supplied ID is only reused if it is well-formed,
 * so clients cannot inject log-forging content.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    private static final Logger access = LoggerFactory.getLogger("trustkart.access");
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : newId();
        long start = System.nanoTime();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000;
            // Path only: query strings can contain search terms or tokens and are not logged.
            access.info("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), response.getStatus(), millis);
            MDC.remove(MDC_KEY);
        }
    }

    static String newId() {
        char[] id = new char[10];
        for (int i = 0; i < id.length; i++) {
            id[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(id);
    }
}
