package com.vijaysinghpuwar.trustkart.auth.application;

import com.vijaysinghpuwar.trustkart.auth.domain.AppUser;
import com.vijaysinghpuwar.trustkart.auth.domain.LoginOutcome;
import com.vijaysinghpuwar.trustkart.auth.domain.UserSession;
import com.vijaysinghpuwar.trustkart.auth.infra.AppUserRepository;
import com.vijaysinghpuwar.trustkart.auth.infra.AuthJdbcRepository;
import com.vijaysinghpuwar.trustkart.auth.infra.UserSessionRepository;
import com.vijaysinghpuwar.trustkart.common.error.ApiError;
import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.common.error.ValidationException;
import com.vijaysinghpuwar.trustkart.security.AuthProperties;
import com.vijaysinghpuwar.trustkart.security.ClientInfo;
import com.vijaysinghpuwar.trustkart.security.SessionRevocation;
import com.vijaysinghpuwar.trustkart.security.Tokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login, refresh-token rotation and logout.
 *
 * <p>Refresh tokens have the form {@code <sessionId>.<secret>}; only SHA-256(secret) is stored. Each refresh
 * rotates the secret and keeps the previous hash. Presenting an already-rotated token means it was copied,
 * so the whole session is revoked (reuse detection). A short grace window avoids false alarms when two
 * browser tabs refresh at the same moment.
 */
@Service
public class AuthService {

    /** Result of a successful sign-in or refresh: the raw tokens to place in cookies. */
    public record Issued(AppUser user, UUID sessionId, String accessToken, String refreshToken) {}

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration REFRESH_GRACE = Duration.ofSeconds(15);

    private final AppUserRepository users;
    private final UserSessionRepository sessions;
    private final AuthJdbcRepository authJdbc;
    private final PasswordEncoder passwords;
    private final TokenIssuer tokens;
    private final SessionRevocation revocation;
    private final AuthProperties props;
    private final Clock clock;
    /** Hash of a random password, verified against when the email is unknown so timing doesn't reveal it. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, UserSessionRepository sessions, AuthJdbcRepository authJdbc,
            PasswordEncoder passwords, TokenIssuer tokens, SessionRevocation revocation, AuthProperties props, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.authJdbc = authJdbc;
        this.passwords = passwords;
        this.tokens = tokens;
        this.revocation = revocation;
        this.props = props;
        this.clock = clock;
        this.dummyHash = passwords.encode(Tokens.random());
    }

    @Transactional
    public Issued register(String email, String password, String displayName, ClientInfo client) {
        String normalizedEmail = email.strip();
        List<String> problems = PasswordPolicy.problems(password, normalizedEmail, displayName);
        if (!problems.isEmpty()) {
            throw new ValidationException(problems.stream().map(p -> new ApiError.FieldError("password", p)).toList());
        }
        if (users.existsByEmail(normalizedEmail)) {
            // Registration necessarily reveals that an address is taken; per-IP rate limiting bounds enumeration.
            throw new ApiException(ErrorCode.ACCOUNT_EXISTS);
        }
        Instant now = clock.instant();
        AppUser user = users.save(new AppUser(normalizedEmail, displayName.strip(), passwords.encode(password), now));
        authJdbc.assignRole(user.getId(), "CUSTOMER");
        user.recordSuccessfulLogin(now);
        return startSession(user, client, now);
    }

    /**
     * Failed attempts must be persisted even though the method throws, hence noRollbackFor.
     * Every failure path returns the same error so responses don't reveal which emails exist.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Issued login(String email, String password, ClientInfo client) {
        Instant now = clock.instant();
        String emailHash = Tokens.emailHash(email);
        Optional<AppUser> found = users.findByEmail(email.strip());
        if (found.isEmpty()) {
            passwords.matches(password, dummyHash);
            authJdbc.recordLogin(null, emailHash, LoginOutcome.BAD_CREDENTIALS, client.ip(), client.userAgent(), null);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        AppUser user = found.get();
        if (user.isLocked(now)) {
            authJdbc.recordLogin(user.getId(), emailHash, LoginOutcome.LOCKED, client.ip(), client.userAgent(), null);
            long retry = Math.max(1, Duration.between(now, user.getLockedUntil()).toSeconds());
            throw new ApiException(ErrorCode.RATE_LIMITED, "Too many attempts. Try again in "
                    + Math.max(1, retry / 60) + " minutes or reset your password.", Map.of("retryAfterSeconds", retry));
        }
        if (!passwords.matches(password, user.getPasswordHash())) {
            boolean locked = user.recordFailedLogin(props.maxFailedLogins(), props.lockDuration(), now);
            authJdbc.recordLogin(user.getId(), emailHash, locked ? LoginOutcome.LOCKED : LoginOutcome.BAD_CREDENTIALS,
                    client.ip(), client.userAgent(), null);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() == AppUser.Status.DISABLED) {
            authJdbc.recordLogin(user.getId(), emailHash, LoginOutcome.DISABLED, client.ip(), client.userAgent(), null);
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (passwords.upgradeEncoding(user.getPasswordHash())) {
            user.upgradePasswordHash(passwords.encode(password));
        }
        user.recordSuccessfulLogin(now);
        Issued issued = startSession(user, client, now);
        authJdbc.recordLogin(user.getId(), emailHash, LoginOutcome.SUCCESS, client.ip(), client.userAgent(), issued.sessionId());
        return issued;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Issued refresh(String rawRefreshToken, ClientInfo client) {
        ParsedToken parsed = ParsedToken.parse(rawRefreshToken).orElseThrow(() -> new ApiException(ErrorCode.SESSION_EXPIRED));
        UserSession session = sessions.findForUpdate(parsed.sessionId())
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_EXPIRED));
        Instant now = clock.instant();
        String presented = Tokens.sha256(parsed.secret());

        if (Tokens.matches(presented, session.getPreviousTokenHash())) {
            if (session.isActive(now) && session.getLastUsedAt().plus(REFRESH_GRACE).isAfter(now)) {
                // Another tab rotated this token moments ago and its response already set fresh cookies.
                throw new ApiException(ErrorCode.REFRESH_IN_PROGRESS);
            }
            session.revoke("REFRESH_REUSE", now);
            revocation.markRevoked(session.getId());
            authJdbc.recordLogin(session.getUserId(), "-", LoginOutcome.REFRESH_REUSE_DETECTED, client.ip(),
                    client.userAgent(), session.getId());
            log.warn("Refresh token reuse detected; session {} revoked", session.getId());
            throw new ApiException(ErrorCode.SESSION_EXPIRED);
        }
        if (!Tokens.matches(presented, session.getTokenHash()) || !session.isActive(now)) {
            throw new ApiException(ErrorCode.SESSION_EXPIRED);
        }
        AppUser user = users.findById(session.getUserId()).orElseThrow(() -> new ApiException(ErrorCode.SESSION_EXPIRED));
        if (user.getStatus() == AppUser.Status.DISABLED) {
            session.revoke("ACCOUNT_DISABLED", now);
            throw new ApiException(ErrorCode.SESSION_EXPIRED);
        }
        String secret = Tokens.random();
        session.rotate(Tokens.sha256(secret), now, client.ip());
        String access = tokens.accessToken(user, session.getId(), authJdbc.roles(user.getId()), authJdbc.permissions(user.getId()), now);
        return new Issued(user, session.getId(), access, session.getId() + "." + secret);
    }

    /** Idempotent: an unknown or already-revoked token still results in cleared cookies. */
    @Transactional
    public void logout(String rawRefreshToken, UUID sessionFromAccessToken, ClientInfo client) {
        Instant now = clock.instant();
        Optional<UUID> sessionId = ParsedToken.parse(rawRefreshToken).map(ParsedToken::sessionId)
                .or(() -> Optional.ofNullable(sessionFromAccessToken));
        sessionId.flatMap(sessions::findById).ifPresent(s -> {
            s.revoke("LOGOUT", now);
            revocation.markRevoked(s.getId());
            authJdbc.recordLogin(s.getUserId(), "-", LoginOutcome.LOGOUT, client.ip(), client.userAgent(), s.getId());
        });
    }

    private Issued startSession(AppUser user, ClientInfo client, Instant now) {
        UUID sessionId = UUID.randomUUID();
        String secret = Tokens.random();
        sessions.save(new UserSession(sessionId, user.getId(), Tokens.sha256(secret), now, now.plus(props.refreshTokenTtl()),
                client.ip(), client.userAgent(), client.deviceLabel()));
        String access = tokens.accessToken(user, sessionId, authJdbc.roles(user.getId()), authJdbc.permissions(user.getId()), now);
        return new Issued(user, sessionId, access, sessionId + "." + secret);
    }

    record ParsedToken(UUID sessionId, String secret) {
        static Optional<ParsedToken> parse(String raw) {
            if (raw == null || raw.length() > 200) {
                return Optional.empty();
            }
            int dot = raw.indexOf('.');
            if (dot < 0) {
                return Optional.empty();
            }
            try {
                return Optional.of(new ParsedToken(UUID.fromString(raw.substring(0, dot)), raw.substring(dot + 1)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }
}
