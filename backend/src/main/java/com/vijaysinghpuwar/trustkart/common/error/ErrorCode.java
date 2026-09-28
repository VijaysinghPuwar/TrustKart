package com.vijaysinghpuwar.trustkart.common.error;

import org.springframework.http.HttpStatus;

/** Stable, machine-readable error codes. The frontend switches on these, never on messages. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "One or more fields are invalid."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request body could not be read."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Sign in to continue."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "That email and password combination didn't work."),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "Your session has ended. Please sign in again."),
    REFRESH_IN_PROGRESS(HttpStatus.CONFLICT, "Your session was just refreshed. Retry the request."),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "This account has been disabled."),
    ACCOUNT_EXISTS(HttpStatus.CONFLICT, "An account with this email already exists. Try signing in."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You don't have access to this."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "We couldn't find that."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "That action isn't supported here."),
    CONFLICT(HttpStatus.CONFLICT, "That conflicts with the current state."),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "Some items are no longer available in that quantity."),
    INSUFFICIENT_VIRTUAL_FUNDS(HttpStatus.UNPROCESSABLE_CONTENT, "Your virtual balance is too low for this purchase."),
    CART_EMPTY(HttpStatus.UNPROCESSABLE_CONTENT, "Your cart is empty."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "An Idempotency-Key header is required."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please wait and try again."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side.");

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
