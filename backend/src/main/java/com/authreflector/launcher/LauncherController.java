package com.authreflector.launcher;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.authreflector.security.AuthenticatedUser;

/** The signed-in user's app launcher: every launchable app, with how much this user uses it. */
@RestController
public class LauncherController {

    static final int RECENT_DAYS = 30;

    /** used = signed in at least once (glowing); unused apps are shown faded. */
    public record LauncherApp(
            String clientId, String name, String description, String homeUrl, String color,
            boolean used, long signInsLast30Days, Instant lastUsedAt) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public LauncherController(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // Order: used before unused; then most sign-ins in the last 30 days; then most recent; then by name
    @GetMapping("/api/v1/launcher")
    public List<LauncherApp> launcher(@AuthenticationPrincipal AuthenticatedUser user) {
        LocalDate since = LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(RECENT_DAYS - 1L);
        return jdbc.query("""
                select a.client_id, c.client_name, a.description, a.home_url, a.color,
                       coalesce(sum(u.sign_ins) filter (where u.day >= ?), 0) as recent,
                       max(u.last_sign_in) as last_used
                from client_apps a
                join oauth2_registered_client c on c.client_id = a.client_id
                left join user_app_daily_usage u on u.client_id = a.client_id and u.user_id = ?
                where a.enabled and a.show_in_launcher and a.home_url is not null
                group by a.client_id, c.client_name, a.description, a.home_url, a.color
                order by (max(u.last_sign_in) is null), recent desc, last_used desc nulls last, lower(c.client_name)
                """,
                (row, index) -> {
                    Timestamp lastUsed = row.getTimestamp("last_used");
                    return new LauncherApp(
                            row.getString("client_id"),
                            row.getString("client_name"),
                            row.getString("description"),
                            row.getString("home_url"),
                            row.getString("color"),
                            lastUsed != null,
                            row.getLong("recent"),
                            lastUsed == null ? null : lastUsed.toInstant());
                },
                since, user.getId());
    }
}
