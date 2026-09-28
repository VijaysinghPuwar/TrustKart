package com.vijaysinghpuwar.trustkart.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Who is calling, for rate limiting and login history. The remote address is resolved by Tomcat's
 * RemoteIpValve, which only trusts X-Forwarded-For from configured internal proxies, so it cannot be spoofed
 * by a client sending the header directly.
 */
public record ClientInfo(String ip, String userAgent) {

    private static final int MAX_USER_AGENT = 300;

    public static ClientInfo from(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.length() > MAX_USER_AGENT) {
            ua = ua.substring(0, MAX_USER_AGENT);
        }
        return new ClientInfo(request.getRemoteAddr(), ua);
    }

    /** A short human label for session lists: "Chrome on macOS". Never used for security decisions. */
    public String deviceLabel() {
        if (userAgent == null) {
            return "Unknown device";
        }
        String ua = userAgent;
        String browser = ua.contains("Edg/") ? "Edge" : ua.contains("Firefox/") ? "Firefox"
                : ua.contains("Chrome/") ? "Chrome" : ua.contains("Safari/") ? "Safari" : "Browser";
        String os = ua.contains("iPhone") || ua.contains("iPad") ? "iOS" : ua.contains("Android") ? "Android"
                : ua.contains("Mac OS X") ? "macOS" : ua.contains("Windows") ? "Windows"
                : ua.contains("Linux") ? "Linux" : "unknown OS";
        return browser + " on " + os;
    }
}
