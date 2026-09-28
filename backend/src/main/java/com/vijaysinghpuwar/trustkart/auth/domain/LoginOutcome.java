package com.vijaysinghpuwar.trustkart.auth.domain;

public enum LoginOutcome {
    SUCCESS,
    BAD_CREDENTIALS,
    LOCKED,
    DISABLED,
    RATE_LIMITED,
    REFRESH_REUSE_DETECTED,
    LOGOUT
}
