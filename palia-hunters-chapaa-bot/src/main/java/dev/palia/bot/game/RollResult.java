package dev.palia.bot.game;

public record RollResult(
        int roll,
        int fromSpace,
        int landingSpace,
        int finalSpace,
        boolean specialMovement,
        boolean previouslyCompleted,
        boolean finalChallenge,
        String challenge
) {}
