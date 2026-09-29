package com.vijaysinghpuwar.trustkart.leaderboard;

import com.vijaysinghpuwar.trustkart.common.error.ApiException;
import com.vijaysinghpuwar.trustkart.common.error.ErrorCode;
import com.vijaysinghpuwar.trustkart.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public virtual-spending rankings. Nothing here accepts an amount: every total comes from order history on the
 * server, and responses never include emails or internal ids.
 */
@RestController
@RequestMapping("/api/v1/leaderboards")
@Tag(name = "Leaderboards", description = "Top virtual spenders. All amounts are virtual; no real money is involved.")
class LeaderboardController {

    record ProfileBody(@NotNull @Size(max = 40) String displayName, @NotNull Boolean visible, @NotNull Boolean showAvatar) {}

    private final LeaderboardService leaderboards;
    private final LeaderboardProfiles profiles;
    private final Clock clock;

    LeaderboardController(LeaderboardService leaderboards, LeaderboardProfiles profiles, Clock clock) {
        this.leaderboards = leaderboards;
        this.profiles = profiles;
        this.clock = clock;
    }

    @GetMapping("/monthly")
    @Operation(summary = "This month's top 50 (UTC calendar month)")
    LeaderboardService.Board monthly() {
        return leaderboards.board(LeaderboardPeriod.currentMonth(clock), viewer());
    }

    @GetMapping("/all-time")
    @Operation(summary = "All-time top 100")
    LeaderboardService.Board allTime() {
        return leaderboards.board(LeaderboardPeriod.allTime(), viewer());
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user's monthly and all-time position, even outside the top lists")
    LeaderboardService.Standing me() {
        return leaderboards.standing(requireUser());
    }

    @GetMapping("/profile")
    LeaderboardProfiles.Profile profile() {
        return profiles.getOrCreate(requireUser());
    }

    @PutMapping("/profile")
    @Operation(summary = "Choose a public name and whether to appear by name on public leaderboards")
    LeaderboardProfiles.Profile updateProfile(@Valid @RequestBody ProfileBody body) {
        return profiles.update(requireUser(), body.displayName(), body.visible(), body.showAvatar());
    }

    private static Long viewer() {
        return AuthenticatedUser.current().map(AuthenticatedUser::userId).orElse(null);
    }

    private static long requireUser() {
        return AuthenticatedUser.current().map(AuthenticatedUser::userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }
}
