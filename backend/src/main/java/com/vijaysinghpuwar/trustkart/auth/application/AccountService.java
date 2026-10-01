package com.vijaysinghpuwar.trustkart.auth.application;

import com.vijaysinghpuwar.trustkart.auth.domain.AppUser;
import com.vijaysinghpuwar.trustkart.auth.domain.UserSession;
import com.vijaysinghpuwar.trustkart.auth.infra.AppUserRepository;
import com.vijaysinghpuwar.trustkart.auth.infra.AuthJdbcRepository;
import com.vijaysinghpuwar.trustkart.auth.infra.UserSessionRepository;
import com.vijaysinghpuwar.trustkart.common.error.ApiError;
import com.vijaysinghpuwar.trustkart.common.error.NotFoundException;
import com.vijaysinghpuwar.trustkart.common.error.ValidationException;
import com.vijaysinghpuwar.trustkart.security.AuthenticatedUser;
import com.vijaysinghpuwar.trustkart.security.ClientInfo;
import com.vijaysinghpuwar.trustkart.security.RateLimitPolicy;
import com.vijaysinghpuwar.trustkart.security.RateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service account security: profile, active sessions, login history, password change. */
@Service
public class AccountService {

    public record Profile(UUID id, String email, String displayName, boolean emailVerified, String avatarUrl, Instant memberSince,
            Instant passwordChangedAt, List<String> roles, List<String> permissions, List<String> linkedProviders) {}

    public record SessionView(UUID id, String device, String ipAddress, Instant createdAt, Instant lastUsedAt, boolean current) {}

    public record LoginEventView(long id, String outcome, String method, String ipAddress, String device, Instant at) {}

    private final AppUserRepository users;
    private final UserSessionRepository sessions;
    private final AuthJdbcRepository authJdbc;
    private final PasswordEncoder passwords;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public AccountService(AppUserRepository users, UserSessionRepository sessions, AuthJdbcRepository authJdbc,
            PasswordEncoder passwords, RateLimiter rateLimiter, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.authJdbc = authJdbc;
        this.passwords = passwords;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Profile profile(AuthenticatedUser me) {
        AppUser user = users.findById(me.userId()).orElseThrow(() -> new NotFoundException("Account"));
        return new Profile(user.getPublicId(), user.getEmail(), user.getDisplayName(), user.isEmailVerified(), user.getAvatarUrl(),
                user.getCreatedAt(), user.getPasswordChangedAt(), authJdbc.roles(user.getId()), authJdbc.permissions(user.getId()),
                authJdbc.linkedProviders(user.getId()));
    }

    @Transactional(readOnly = true)
    public List<SessionView> sessions(AuthenticatedUser me) {
        return sessions.findActive(me.userId(), clock.instant()).stream()
                .map(s -> new SessionView(s.getId(), s.getDeviceLabel(), s.getIpAddress(), s.getCreatedAt(), s.getLastUsedAt(),
                        s.getId().equals(me.sessionId())))
                .toList();
    }

    /** Sessions are looked up by (id, owner): someone else's session id behaves exactly like a missing one. */
    @Transactional
    public void revokeSession(AuthenticatedUser me, UUID sessionId) {
        UserSession session = sessions.findByIdAndUserId(sessionId, me.userId())
                .orElseThrow(() -> new NotFoundException("Session"));
        session.revoke("USER_REVOKED", clock.instant());
    }

    @Transactional
    public int revokeOtherSessions(AuthenticatedUser me) {
        Instant now = clock.instant();
        List<UserSession> others = sessions.findActive(me.userId(), now).stream()
                .filter(s -> !s.getId().equals(me.sessionId())).toList();
        others.forEach(s -> s.revoke("USER_REVOKED_OTHERS", now));
        return others.size();
    }

    @Transactional(readOnly = true)
    public List<LoginEventView> loginHistory(AuthenticatedUser me) {
        return authJdbc.recentLogins(me.userId(), 50).stream()
                .map(e -> new LoginEventView(e.id(), e.outcome(), e.method(), e.ipAddress(), new ClientInfo(e.ipAddress(), e.userAgent()).deviceLabel(),
                        e.createdAt()))
                .toList();
    }

    /** Requires the current password and signs out every other session afterwards. */
    @Transactional
    public void changePassword(AuthenticatedUser me, String currentPassword, String newPassword) {
        rateLimiter.check(RateLimitPolicy.PASSWORD_CHANGE_PER_USER, Long.toString(me.userId()));
        AppUser user = users.findById(me.userId()).orElseThrow(() -> new NotFoundException("Account"));
        if (!passwords.matches(currentPassword, user.getPasswordHash())) {
            throw new ValidationException("currentPassword", "Your current password is incorrect.");
        }
        List<String> problems = PasswordPolicy.problems(newPassword, user.getEmail(), user.getDisplayName());
        if (!problems.isEmpty()) {
            throw new ValidationException(problems.stream().map(p -> new ApiError.FieldError("newPassword", p)).toList());
        }
        user.changePassword(passwords.encode(newPassword), clock.instant());
        revokeOtherSessions(me);
    }
}
