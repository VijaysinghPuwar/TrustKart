package com.vijaysinghpuwar.trustkart.auth.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Length-first password rules in line with NIST SP 800-63B: at least 12 characters, no composition rules,
 * reject known-common passwords and ones built from the user's own email or name.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    // A denylist of well-known weak passwords, not credentials.
    private static final Set<String> COMMON = Set.of( // gitleaks:allow
            "password1234", "123456789012", "qwertyuiop12", "password123!", "letmein12345", "iloveyou1234",
            "trustkart123", "trustkart1234", "welcome12345", "administrator", "passwordpassword", "qwerty123456", // gitleaks:allow
            "1q2w3e4r5t6y", "abc123456789", "000000000000", "111111111111", "changeme1234", "football1234");

    private PasswordPolicy() {}

    /** Returns the problems with a candidate password; empty means acceptable. */
    public static List<String> problems(String password, String email, String displayName) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            problems.add("Use at least " + MIN_LENGTH + " characters.");
            return problems;
        }
        if (password.length() > MAX_LENGTH) {
            problems.add("Use at most " + MAX_LENGTH + " characters.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower) || lower.chars().distinct().count() <= 3) {
            problems.add("This password is too common or repetitive.");
        }
        String local = email == null ? "" : email.toLowerCase(Locale.ROOT).split("@")[0];
        if (local.length() >= 4 && lower.contains(local)) {
            problems.add("Don't include your email address.");
        }
        String name = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (name.length() >= 4 && lower.contains(name)) {
            problems.add("Don't include your name.");
        }
        return problems;
    }
}
