package dev.palia.bot.game;

public record ProofSubmission(
        long id,
        long guildId,
        long userId,
        int space,
        String status,
        String reviewerNote,
        Long reviewerUserId,
        String createdAt,
        String reviewedAt
) {}
