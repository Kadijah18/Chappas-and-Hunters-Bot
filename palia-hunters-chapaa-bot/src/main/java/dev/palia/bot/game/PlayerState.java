package dev.palia.bot.game;

public record PlayerState(
        long guildId,
        long userId,
        String displayName,
        int position,
        boolean challengePending,
        boolean proofPending,
        Long threadId,
        boolean winner
) {}
