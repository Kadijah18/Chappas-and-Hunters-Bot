package dev.palia.bot.discord;

import dev.palia.bot.config.BotConfig;
import dev.palia.bot.game.GameService;
import dev.palia.bot.game.PlayerState;
import dev.palia.bot.game.Proof;
import dev.palia.bot.game.ProofSubmission;
import dev.palia.bot.game.RollResult;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class GameCommandListener extends ListenerAdapter {
    private final BotConfig config;
    private final GameService game;

    public GameCommandListener(BotConfig config, GameService game) {
        this.config = config;
        this.game = game;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null) {
            event.reply("This game can only be played inside a Discord server.").setEphemeral(true).queue();
            return;
        }

        try {
            switch (event.getName()) {
                case "join" -> join(event);
                case "roll" -> roll(event);
                case "challenge" -> challenge(event);
                case "complete" -> complete(event);
                case "proof-status" -> proofStatus(event);
                case "verify" -> verify(event);
                case "position" -> position(event);
                case "trail" -> trail(event);
                case "leaderboard" -> leaderboard(event);
                case "reset" -> reset(event);
                default -> event.reply("Unknown command.").setEphemeral(true).queue();
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            if (!event.isAcknowledged()) event.reply("🛑 " + e.getMessage()).setEphemeral(true).queue();
        } catch (Exception e) {
            e.printStackTrace();
            if (!event.isAcknowledged()) {
                event.reply("Something went wrong while handling that command.").setEphemeral(true).queue();
            }
        }
    }

    private void join(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        long userId = event.getUser().getIdLong();
        String displayName = event.getMember() != null ? event.getMember().getEffectiveName() : event.getUser().getName();

        if (!config.gameChannelId().isBlank() && !event.getChannel().getId().equals(config.gameChannelId())) {
            throw new IllegalStateException("Please use /join in the configured Hunters & Chapaa game channel.");
        }

        PlayerState player = game.join(guildId, userId, displayName);
        if (player.threadId() != null) {
            var existing = event.getGuild().getThreadChannelById(player.threadId());
            if (existing != null) {
                if (existing.isArchived()) existing.getManager().setArchived(false).queue();
                event.reply("🐾 You are already in the race. Your player thread is " + existing.getAsMention()).setEphemeral(true).queue();
                return;
            }
        }

        if (!config.playerThreadsEnabled()) {
            event.reply("🐾 " + event.getUser().getAsMention() + " joined the Hunters & Chapaa challenge race at **Space " + config.startSpace() + "**!").queue();
            return;
        }

        TextChannel parent;
        if (!config.gameChannelId().isBlank()) {
            parent = event.getGuild().getTextChannelById(config.gameChannelId());
            if (parent == null) throw new IllegalStateException("The configured game.channel.id is not a text channel the bot can access.");
        } else if (event.getChannelType() == ChannelType.TEXT) {
            parent = event.getChannel().asTextChannel();
        } else {
            throw new IllegalStateException("Use /join in a normal text channel so I can create your player thread.");
        }

        String threadName = sanitizeThreadName(displayName) + " — hunters-chapaa";
        event.deferReply(true).queue(hook -> parent.createThreadChannel(threadName)
                .queue(thread -> {
                    game.setThread(guildId, userId, thread.getIdLong());
                    thread.sendMessage("🐾 Welcome " + event.getUser().getAsMention() + "! This is your Hunters & Chapaa race thread.\n\n"
                            + "Start at **Space " + config.startSpace() + "** and use `/roll`. Complete the challenge, then submit `/complete` with up to "
                            + config.maxProofAttachments() + " screenshots. Your proof must be approved by a moderator before you can roll again.").queue();
                    hook.editOriginal("✅ Joined! Your player thread is " + thread.getAsMention()).queue();
                }, error -> hook.editOriginal("I joined you to the game, but I could not create your thread: " + error.getMessage()).queue()));
    }

    private void roll(SlashCommandInteractionEvent event) {
        PlayerState player = requireOwnThread(event);
        RollResult result = game.roll(player.guildId(), player.userId());
        StringBuilder out = new StringBuilder();
        out.append("🎲 **").append(event.getUser().getName()).append(" rolled ").append(result.roll()).append("!**\n")
                .append("**").append(result.fromSpace()).append(" → ").append(result.landingSpace()).append("**");

        if (result.specialMovement()) out.append("\n🐾 Board movement sends you to **Space ").append(result.finalSpace()).append("**.");

        if (result.previouslyCompleted() && config.completedSpaceIsFree() && !result.finalChallenge()) {
            out.append("\n\n✅ You previously completed **Space ").append(result.finalSpace()).append("**. It is a **FREE SPACE** — you may `/roll` again.");
        } else {
            out.append("\n\n### Challenge #").append(result.finalSpace()).append("\n").append(result.challenge());
            out.append("\n\n📸 When finished, use `/complete` to submit screenshot proof for moderator verification.");
            if (result.finalChallenge()) out.append("\n🏆 Space 90 is only won after the final proof is approved.");
        }
        event.reply(out.toString()).queue();
    }

    private void challenge(SlashCommandInteractionEvent event) {
        PlayerState player = requireOwnThread(event);
        String status = player.proofPending() ? "🔎 Proof awaiting moderator verification"
                : player.challengePending() ? "🟡 Challenge in progress" : "✅ Ready to roll";
        event.reply("### Space " + player.position() + "\n" + game.challenge(player.guildId(), player.userId()) + "\n\n**Status:** " + status).queue();
    }

    private void complete(SlashCommandInteractionEvent event) {
        PlayerState player = requireOwnThread(event);
        List<Proof> proofs = readProofs(event);
        long submissionId = game.submitProof(player.guildId(), player.userId(), proofs);

        StringBuilder out = new StringBuilder("📨 **Challenge #" + player.position() + " proof submitted for verification.**");
        out.append("\n**Submission ID:** `").append(submissionId).append("`");
        if (!proofs.isEmpty()) {
            out.append("\n📸 Screenshots: **").append(proofs.size()).append("**");
            for (int i = 0; i < proofs.size(); i++) {
                out.append("\n• [Proof ").append(i + 1).append("](").append(proofs.get(i).url()).append(") — ").append(proofs.get(i).fileName());
            }
        }
        out.append("\n\n🔎 A moderator must approve this submission before you can `/roll` again. Use `/proof-status` to check it.");
        event.reply(out.toString()).queue();
    }

    private void proofStatus(SlashCommandInteractionEvent event) {
        PlayerState player = requireOwnThread(event);
        ProofSubmission submission = game.proofStatus(player.guildId(), player.userId());
        String icon = switch (submission.status()) {
            case "APPROVED" -> "✅";
            case "REJECTED" -> "❌";
            default -> "🔎";
        };
        StringBuilder out = new StringBuilder(icon + " **Proof Submission #" + submission.id() + "**\n")
                .append("Space: **").append(submission.space()).append("**\n")
                .append("Status: **").append(submission.status()).append("**");
        if (submission.reviewerNote() != null && !submission.reviewerNote().isBlank()) {
            out.append("\nModerator note: ").append(submission.reviewerNote());
        }
        event.reply(out.toString()).setEphemeral(true).queue();
    }

    private void verify(SlashCommandInteractionEvent event) {
        requireModerator(event);
        long submissionId = Objects.requireNonNull(event.getOption("submission")).getAsLong();
        String action = Objects.requireNonNull(event.getOption("action")).getAsString();
        String note = event.getOption("reason") == null ? "" : event.getOption("reason").getAsString().trim();
        boolean approve = "approve".equalsIgnoreCase(action);
        if (!approve && !"reject".equalsIgnoreCase(action)) throw new IllegalArgumentException("Action must be approve or reject.");

        ProofSubmission submission = game.requireSubmission(event.getGuild().getIdLong(), submissionId);
        List<Proof> proofs = game.proofsForSubmission(submissionId);
        PlayerState updated = game.verify(event.getGuild().getIdLong(), submissionId, event.getUser().getIdLong(), approve, note);

        StringBuilder out = new StringBuilder();
        if (approve) {
            out.append("✅ **Proof Submission #").append(submissionId).append(" approved.**\n")
                    .append("<@").append(submission.userId()).append("> completed **Space ").append(submission.space()).append("**.");
            if (updated.winner()) out.append("\n\n👑🏆 **HUNTERS & CHAPAA CHAMPION! Space 90 has been verified.**");
            else out.append("\n🎲 The player may roll again.");
        } else {
            out.append("❌ **Proof Submission #").append(submissionId).append(" rejected.**\n")
                    .append("<@").append(submission.userId()).append("> must submit new proof for **Space ").append(submission.space()).append("**.");
        }
        if (!note.isBlank()) out.append("\n**Moderator note:** ").append(note);
        if (!proofs.isEmpty()) out.append("\n**Reviewed screenshots:** ").append(proofs.size());
        event.reply(out.toString()).queue();
    }

    private void position(SlashCommandInteractionEvent event) {
        PlayerState player = requireOwnThread(event);
        String status = player.proofPending() ? "proof awaiting verification."
                : player.challengePending() ? "challenge in progress." : "ready to roll.";
        event.reply("🐾 You are on **Space " + player.position() + " / " + config.finalSpace() + "** — " + status).queue();
    }

    private void trail(SlashCommandInteractionEvent event) {
        PlayerState player = requireOwnThread(event);
        List<Integer> completed = game.completedSpaces(player.guildId(), player.userId());
        String text = completed.isEmpty() ? "None yet." : completed.stream().map(String::valueOf).collect(Collectors.joining(", "));
        event.reply("🗺️ **Your verified completed trail:** " + text).queue();
    }

    private void leaderboard(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        List<PlayerState> players = game.leaderboard(guildId);
        if (players.isEmpty()) {
            event.reply("No one has joined the Hunters & Chapaa race yet.").queue();
            return;
        }
        StringBuilder out = new StringBuilder("## 🏆 Hunters & Chapaa Leaderboard\n");
        for (int i = 0; i < players.size(); i++) {
            PlayerState p = players.get(i);
            String status = p.winner() ? "👑 WINNER" : p.proofPending() ? "🔎 Proof Review" : p.challengePending() ? "🟡 Challenge" : "✅ Ready";
            out.append(i + 1).append(". **").append(p.displayName()).append("** — Space **").append(p.position()).append("/90** ").append(status).append("\n");
        }
        event.reply(out.toString()).queue();
    }

    private void reset(SlashCommandInteractionEvent event) {
        requireModerator(event);
        game.reset(event.getGuild().getIdLong());
        event.reply("♻️ The Hunters & Chapaa race has been reset for this server.").queue();
    }

    private void requireModerator(SlashCommandInteractionEvent event) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            throw new IllegalStateException("Only a server moderator with Manage Server can use this command.");
        }
    }

    private PlayerState requireOwnThread(SlashCommandInteractionEvent event) {
        PlayerState player = game.requirePlayer(event.getGuild().getIdLong(), event.getUser().getIdLong());
        if (config.playerThreadsEnabled() && player.threadId() != null && event.getChannel().getIdLong() != player.threadId()) {
            ThreadChannel thread = event.getGuild().getThreadChannelById(player.threadId());
            String where = thread == null ? "your assigned player thread" : thread.getAsMention();
            throw new IllegalStateException("Use this command inside " + where + ".");
        }
        return player;
    }

    private List<Proof> readProofs(SlashCommandInteractionEvent event) {
        List<Proof> proofs = new ArrayList<>();
        for (int i = 1; i <= config.maxProofAttachments(); i++) {
            var option = event.getOption("proof" + i);
            if (option == null) continue;
            Message.Attachment attachment = option.getAsAttachment();
            String contentType = attachment.getContentType();
            if (contentType != null && !contentType.startsWith("image/")) {
                throw new IllegalArgumentException("Proof files must be images/screenshots. " + attachment.getFileName() + " is not an image.");
            }
            proofs.add(new Proof(attachment.getFileName(), contentType, attachment.getUrl()));
        }
        return proofs;
    }

    private String sanitizeThreadName(String name) {
        String cleaned = Objects.requireNonNullElse(name, "player")
                .replaceAll("[^\\p{L}\\p{N} _-]", "")
                .trim();
        if (cleaned.isBlank()) cleaned = "player";
        if (cleaned.length() > 70) cleaned = cleaned.substring(0, 70);
        return cleaned;
    }
}
