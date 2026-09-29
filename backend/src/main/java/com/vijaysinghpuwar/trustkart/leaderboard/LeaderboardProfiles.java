package com.vijaysinghpuwar.trustkart.leaderboard;

import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.common.error.ValidationException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each account's public leaderboard identity.
 *
 * <p><b>Privacy model:</b> hidden by default. A hidden account is still ranked (so ranks stay honest) but appears as
 * "Anonymous collector" with no avatar. The public name is a separate alias, never the email or the account's
 * display name, generated as "Collector" plus digits until the user chooses one. A profile photo is shown only if
 * the user opts in. Deleted accounts disappear with their orders (ON DELETE CASCADE), so nothing identifying remains.
 */
@Service
public class LeaderboardProfiles {

    public record Profile(String displayName, boolean visible, boolean showAvatar) {}

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,20}");
    /** A short blocklist of names that impersonate the store or its staff; real moderation would go further. */
    private static final Set<String> RESERVED = Set.of("admin", "administrator", "trustkart", "support", "moderator",
            "staff", "system", "root", "anonymous", "anonymouscollector", "official");

    private final JdbcClient jdbc;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public LeaderboardProfiles(JdbcClient jdbc, Clock clock, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Optional<Profile> find(long userId) {
        return jdbc.sql("SELECT display_name, visible, show_avatar FROM leaderboard_profile WHERE user_id = :u")
                .param("u", userId)
                .query((rs, i) -> new Profile(rs.getString(1), rs.getBoolean(2), rs.getBoolean(3)))
                .optional();
    }

    /** The profile, created with a generated alias on first use. */
    @Transactional
    public Profile getOrCreate(long userId) {
        return find(userId).orElseGet(() -> {
            for (int attempt = 0; attempt < 20; attempt++) {
                String alias = "Collector" + (10000 + random.nextInt(90000));
                int inserted = jdbc.sql("""
                                INSERT INTO leaderboard_profile (user_id, display_name, updated_at) VALUES (:u, :n, :now)
                                ON CONFLICT DO NOTHING""")
                        .param("u", userId).param("n", alias).param("now", Timestamp.from(clock.instant())).update();
                if (inserted == 1 || find(userId).isPresent()) {
                    return find(userId).orElseThrow();
                }
            }
            throw new ApiException(ErrorCode.CONFLICT, "Could not create a leaderboard name. Please try again.");
        });
    }

    @Transactional
    public Profile update(long userId, String displayName, boolean visible, boolean showAvatar) {
        getOrCreate(userId);
        String name = displayName.strip();
        if (!NAME.matcher(name).matches()) {
            throw new ValidationException("displayName", "Use 3 to 20 letters, numbers or underscores.");
        }
        String folded = name.toLowerCase(Locale.ROOT).replace("_", "");
        if (RESERVED.stream().anyMatch(folded::startsWith)) {
            throw new ValidationException("displayName", "That name is reserved. Please choose another.");
        }
        try {
            jdbc.sql("""
                            UPDATE leaderboard_profile SET display_name = :n, visible = :v, show_avatar = :a, updated_at = :now
                            WHERE user_id = :u""")
                    .param("n", name).param("v", visible).param("a", showAvatar)
                    .param("now", Timestamp.from(clock.instant())).param("u", userId).update();
        } catch (DuplicateKeyException e) {
            throw new ValidationException("displayName", "That name is taken. Please choose another.");
        }
        events.publishEvent(new LeaderboardChanged());
        return find(userId).orElseThrow();
    }
}
