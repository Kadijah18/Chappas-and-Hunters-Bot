package dev.palia.bot.game;

import dev.palia.bot.config.BotConfig;
import dev.palia.bot.db.PlayerRepository;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class GameService {
    private final BotConfig config;
    private final PlayerRepository players;
    private final ChallengeRepository challenges;
    private final BoardRepository board;

    public GameService(BotConfig config, PlayerRepository players, ChallengeRepository challenges, BoardRepository board) {
        this.config = config;
        this.players = players;
        this.challenges = challenges;
        this.board = board;
    }

    public PlayerState join(long guildId, long userId, String displayName) {
        return players.join(guildId, userId, displayName, config.startSpace());
    }

    public PlayerState requirePlayer(long guildId, long userId) {
        return players.find(guildId, userId)
                .orElseThrow(() -> new IllegalStateException("You have not joined this game yet. Use /join first."));
    }

    public void setThread(long guildId, long userId, long threadId) {
        players.setThread(guildId, userId, threadId);
    }

    public RollResult roll(long guildId, long userId) {
        PlayerState player = requirePlayer(guildId, userId);
        if (player.winner()) throw new IllegalStateException("You have already completed the final challenge and won.");
        if (player.proofPending()) throw new IllegalStateException("Your screenshot proof is waiting for moderator verification.");
        if (player.challengePending()) throw new IllegalStateException("Complete your current challenge before rolling again.");

        int roll = ThreadLocalRandom.current().nextInt(1, config.dieSides() + 1);
        int from = player.position();
        int landing = Math.min(config.finalSpace(), from + roll);
        int finalSpace = Math.max(config.startSpace(), Math.min(config.finalSpace(), board.resolve(landing)));
        boolean previouslyCompleted = players.isCompleted(guildId, userId, finalSpace);
        boolean finalChallenge = finalSpace == config.finalSpace();
        boolean pending = finalChallenge || !(config.completedSpaceIsFree() && previouslyCompleted);

        players.updatePosition(guildId, userId, finalSpace, pending);
        return new RollResult(roll, from, landing, finalSpace, board.isSpecial(landing), previouslyCompleted,
                finalChallenge, challenges.get(finalSpace));
    }

    public long submitProof(long guildId, long userId, List<Proof> proofs) {
        PlayerState player = requirePlayer(guildId, userId);
        if (!player.challengePending()) throw new IllegalStateException("You do not currently have a challenge waiting to be completed.");
        if (player.proofPending()) throw new IllegalStateException("You already have proof waiting for moderator verification.");
        if (config.proofRequired() && proofs.isEmpty()) {
            throw new IllegalStateException("At least one screenshot is required before this challenge can be submitted for verification.");
        }
        return players.submitProof(guildId, userId, player.position(), proofs);
    }

    public ProofSubmission proofStatus(long guildId, long userId) {
        requirePlayer(guildId, userId);
        return players.latestSubmission(guildId, userId)
                .orElseThrow(() -> new IllegalStateException("You have not submitted any proof yet."));
    }

    public ProofSubmission requireSubmission(long guildId, long submissionId) {
        return players.findSubmission(guildId, submissionId)
                .orElseThrow(() -> new IllegalStateException("Proof submission #" + submissionId + " was not found."));
    }

    public List<Proof> proofsForSubmission(long submissionId) {
        return players.proofsForSubmission(submissionId);
    }

    public PlayerState verify(long guildId, long submissionId, long reviewerUserId, boolean approve, String note) {
        ProofSubmission submission = requireSubmission(guildId, submissionId);
        PlayerState player = requirePlayer(guildId, submission.userId());
        if (approve) {
            boolean winner = submission.space() == config.finalSpace();
            players.approveSubmission(guildId, submissionId, reviewerUserId, note, winner);
        } else {
            players.rejectSubmission(guildId, submissionId, reviewerUserId, note);
        }
        return requirePlayer(guildId, submission.userId());
    }

    public String challenge(long guildId, long userId) {
        PlayerState player = requirePlayer(guildId, userId);
        return challenges.get(player.position());
    }

    public List<PlayerState> leaderboard(long guildId) {
        return players.findAll(guildId);
    }

    public List<Integer> completedSpaces(long guildId, long userId) {
        return players.completedSpaces(guildId, userId);
    }

    public void reset(long guildId) {
        players.resetGuild(guildId);
    }
}
