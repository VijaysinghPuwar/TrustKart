package com.vijaysinghpuwar.trustkart.auth.infra;

import com.vijaysinghpuwar.trustkart.auth.domain.LoginOutcome;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Roles, permissions and login events: simple relational reads/writes where JPA entities add nothing. */
@Repository
public class AuthJdbcRepository {

    public record LoginEventRow(long id, String outcome, String method, String ipAddress, String userAgent, Instant createdAt) {}

    private final JdbcClient jdbc;

    public AuthJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> roles(long userId) {
        return jdbc.sql("SELECT r.name FROM user_role ur JOIN role r ON r.id = ur.role_id WHERE ur.user_id = :u ORDER BY r.name")
                .param("u", userId).query(String.class).list();
    }

    public List<String> permissions(long userId) {
        return jdbc.sql("""
                        SELECT DISTINCT p.name FROM user_role ur
                        JOIN role_permission rp ON rp.role_id = ur.role_id
                        JOIN permission p ON p.id = rp.permission_id
                        WHERE ur.user_id = :u ORDER BY p.name""")
                .param("u", userId).query(String.class).list();
    }

    public List<String> linkedProviders(long userId) {
        return jdbc.sql("SELECT provider FROM user_identity WHERE user_id = :u ORDER BY provider").param("u", userId).query(String.class).list();
    }

    public void assignRole(long userId, String role) {
        jdbc.sql("""
                        INSERT INTO user_role (user_id, role_id) SELECT :u, id FROM role WHERE name = :r
                        ON CONFLICT DO NOTHING""")
                .param("u", userId).param("r", role).update();
    }

    public void recordLogin(Long userId, String emailHash, LoginOutcome outcome, String ip, String userAgent, UUID sessionId) {
        recordLogin(userId, emailHash, outcome, ip, userAgent, sessionId, "PASSWORD");
    }

    public void recordLogin(Long userId, String emailHash, LoginOutcome outcome, String ip, String userAgent, UUID sessionId,
            String method) {
        jdbc.sql("""
                        INSERT INTO login_event (user_id, email_hash, outcome, ip_address, user_agent, session_id, method)
                        VALUES (:u, :h, :o, :ip, :ua, :sid, :m)""")
                .param("u", userId).param("h", emailHash).param("o", outcome.name())
                .param("ip", ip).param("ua", userAgent).param("sid", sessionId).param("m", method)
                .update();
    }

    public List<LoginEventRow> recentLogins(long userId, int limit) {
        return jdbc.sql("""
                        SELECT id, outcome, method, ip_address, user_agent, created_at FROM login_event
                        WHERE user_id = :u ORDER BY created_at DESC LIMIT :n""")
                .param("u", userId).param("n", limit)
                .query((rs, i) -> new LoginEventRow(rs.getLong("id"), rs.getString("outcome"), rs.getString("method"), rs.getString("ip_address"),
                        rs.getString("user_agent"), rs.getTimestamp("created_at").toInstant()))
                .list();
    }
}
