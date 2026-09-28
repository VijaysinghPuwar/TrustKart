package com.vijaysinghpuwar.trustkart.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Auth cookie policy in one place.
 * <ul>
 *   <li>tk_at: access JWT. HttpOnly, SameSite=Lax, Path=/api, lives exactly as long as the token.</li>
 *   <li>tk_rt: refresh token. HttpOnly, SameSite=Strict, Path=/api/v1/auth so it is only ever sent to the
 *       refresh and logout endpoints, never to the rest of the API.</li>
 *   <li>tk_guest: guest shopper token. HttpOnly, SameSite=Lax, Path=/api.</li>
 * </ul>
 */
@Component
public class AuthCookies {

    public static final String ACCESS = "tk_at";
    public static final String REFRESH = "tk_rt";
    public static final String GUEST = "tk_guest";
    private static final Duration GUEST_TTL = Duration.ofDays(30);

    private final AuthProperties props;

    public AuthCookies(AuthProperties props) {
        this.props = props;
    }

    public void setSession(HttpServletResponse response, String accessToken, String refreshToken) {
        add(response, cookie(ACCESS, accessToken, "/api", "Lax", props.accessTokenTtl()));
        add(response, cookie(REFRESH, refreshToken, "/api/v1/auth", "Strict", props.refreshTokenTtl()));
    }

    public void clearSession(HttpServletResponse response) {
        add(response, cookie(ACCESS, "", "/api", "Lax", Duration.ZERO));
        add(response, cookie(REFRESH, "", "/api/v1/auth", "Strict", Duration.ZERO));
    }

    public void setGuest(HttpServletResponse response, String token) {
        add(response, cookie(GUEST, token, "/api", "Lax", GUEST_TTL));
    }

    public void clearGuest(HttpServletResponse response) {
        add(response, cookie(GUEST, "", "/api", "Lax", Duration.ZERO));
    }

    public static Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank() && c.getValue().length() <= 200) {
                return Optional.of(c.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie cookie(String name, String value, String path, String sameSite, Duration maxAge) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(props.secureCookies()).sameSite(sameSite)
                .path(path).maxAge(maxAge).build();
    }

    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
