package com.vijaysinghpuwar.trustkart.leaderboard;

/**
 * Published when something that affects rankings changes: an order placed, cancelled or returned, or a public
 * profile edited. Handled after the transaction commits, so a rolled-back order never touches the leaderboard.
 */
public record LeaderboardChanged() {}
