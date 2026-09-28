package com.vijaysinghpuwar.trustkart.security.oauth;

import com.vijaysinghpuwar.trustkart.security.AuthCookies;
import com.vijaysinghpuwar.trustkart.security.AuthProperties;
import com.vijaysinghpuwar.trustkart.security.Tokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.Base64;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Keeps the in-flight OAuth2 authorization request (state, nonce, PKCE verifier) in Redis for ten minutes, keyed
 * by a random HttpOnly cookie. The API stays stateless (no HTTP session) and works across instances. The browser
 * only ever holds an opaque key, and deserialisation is restricted by an allow-list filter to the expected types.
 */
public class RedisAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE = "tk_oauth";
    private static final String PREFIX = "tk:oauth-req:";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final ObjectInputFilter FILTER =
            ObjectInputFilter.Config.createFilter("org.springframework.security.**;java.base/*;maxdepth=20;maxbytes=65536;!*");

    private final StringRedisTemplate redis;
    private final AuthProperties props;

    public RedisAuthorizationRequestRepository(StringRedisTemplate redis, AuthProperties props) {
        this.redis = redis;
        this.props = props;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return AuthCookies.read(request, COOKIE).map(k -> redis.opsForValue().get(PREFIX + Tokens.sha256(k)))
                .map(RedisAuthorizationRequestRepository::deserialize).orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request,
            HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }
        String key = Tokens.random();
        redis.opsForValue().set(PREFIX + Tokens.sha256(key), serialize(authorizationRequest), TTL);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(key, TTL).toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest existing = AuthCookies.read(request, COOKIE)
                .map(k -> redis.opsForValue().getAndDelete(PREFIX + Tokens.sha256(k)))
                .map(RedisAuthorizationRequestRepository::deserialize).orElse(null);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
        return existing;
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        // Lax, not Strict: the callback is a top-level navigation coming back from accounts.google.com.
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(props.secureCookies()).sameSite("Lax")
                .path("/api/v1/auth/oauth2").maxAge(maxAge).build();
    }

    private static String serialize(OAuth2AuthorizationRequest request) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(request);
            out.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Could not store OAuth2 authorization request", e);
        }
    }

    private static OAuth2AuthorizationRequest deserialize(String encoded) {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            in.setObjectInputFilter(FILTER);
            return (OAuth2AuthorizationRequest) in.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            return null;
        }
    }
}
