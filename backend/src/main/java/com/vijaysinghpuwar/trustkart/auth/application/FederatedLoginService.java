package com.vijaysinghpuwar.trustkart.auth.application;

import com.vijaysinghpuwar.trustkart.auth.domain.AppUser;
import com.vijaysinghpuwar.trustkart.auth.infra.AppUserRepository;
import com.vijaysinghpuwar.trustkart.auth.infra.AuthJdbcRepository;
import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.security.ClientInfo;
import com.vijaysinghpuwar.trustkart.security.Tokens;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signs a user in from a verified external identity (Google).
 * <ol>
 *   <li>Known (provider, subject): that account.</li>
 *   <li>Otherwise an existing account with the same email is linked, but only if the provider says the email
 *       is verified; an unverified email could belong to anyone.</li>
 *   <li>Otherwise a new account is created. Its password is a random secret nobody knows, so it can only be
 *       used through Google until the user sets one.</li>
 * </ol>
 */
@Service
public class FederatedLoginService {

    public record ExternalProfile(String provider, String subject, String email, boolean emailVerified, String name, String pictureUrl) {}

    private final AppUserRepository users;
    private final AuthJdbcRepository authJdbc;
    private final AuthService auth;
    private final PasswordEncoder passwords;
    private final JdbcClient jdbc;
    private final Clock clock;

    public FederatedLoginService(AppUserRepository users, AuthJdbcRepository authJdbc, AuthService auth, PasswordEncoder passwords,
            JdbcClient jdbc, Clock clock) {
        this.users = users;
        this.authJdbc = authJdbc;
        this.auth = auth;
        this.passwords = passwords;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public AuthService.Issued signIn(ExternalProfile profile, ClientInfo client) {
        if (profile.subject() == null || profile.subject().isBlank() || profile.email() == null || profile.email().isBlank()) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Google didn't share the details we need to sign you in.");
        }
        Instant now = clock.instant();
        AppUser user = findLinked(profile).orElseGet(() -> linkOrCreate(profile, now));
        if (user.getStatus() == AppUser.Status.DISABLED) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }
        jdbc.sql("UPDATE user_identity SET last_used_at = now() WHERE provider = :p AND subject = :s")
                .param("p", profile.provider()).param("s", profile.subject()).update();
        user.updateAvatar(profile.pictureUrl());
        user.recordSuccessfulLogin(now);
        AuthService.Issued issued = auth.startSession(user, client, now);
        authJdbc.recordLogin(user.getId(), Tokens.emailHash(profile.email()), com.vijaysinghpuwar.trustkart.auth.domain.LoginOutcome.SUCCESS,
                client.ip(), client.userAgent(), issued.sessionId(), profile.provider().toUpperCase(Locale.ROOT));
        return issued;
    }

    private Optional<AppUser> findLinked(ExternalProfile profile) {
        return jdbc.sql("SELECT user_id FROM user_identity WHERE provider = :p AND subject = :s")
                .param("p", profile.provider()).param("s", profile.subject()).query(Long.class).optional()
                .flatMap(users::findById);
    }

    private AppUser linkOrCreate(ExternalProfile profile, Instant now) {
        Optional<AppUser> byEmail = users.findByEmail(profile.email().strip());
        AppUser user;
        if (byEmail.isPresent()) {
            if (!profile.emailVerified()) {
                throw new ApiException(ErrorCode.ACCOUNT_EXISTS,
                        "An account with this email already exists. Sign in with your password instead.");
            }
            user = byEmail.get();
        } else {
            String name = profile.name() == null || profile.name().isBlank()
                    ? profile.email().split("@")[0] : profile.name().strip();
            user = users.save(new AppUser(profile.email().strip(), name.length() > 60 ? name.substring(0, 60) : name,
                    passwords.encode(Tokens.random()), now));
            if (profile.emailVerified()) {
                user.markEmailVerified();
            }
            authJdbc.assignRole(user.getId(), "CUSTOMER");
        }
        jdbc.sql("INSERT INTO user_identity (user_id, provider, subject, email) VALUES (:u, :p, :s, :e)")
                .param("u", user.getId()).param("p", profile.provider()).param("s", profile.subject()).param("e", profile.email())
                .update();
        return user;
    }
}
