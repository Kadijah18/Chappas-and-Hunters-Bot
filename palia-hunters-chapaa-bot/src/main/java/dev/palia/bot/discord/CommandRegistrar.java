package dev.palia.bot.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public final class CommandRegistrar {
    private CommandRegistrar() {}

    public static void register(JDA jda) {
        var complete = Commands.slash("complete", "Submit your completed challenge for screenshot verification")
                .addOption(OptionType.ATTACHMENT, "proof1", "Screenshot proof #1", false)
                .addOption(OptionType.ATTACHMENT, "proof2", "Screenshot proof #2", false)
                .addOption(OptionType.ATTACHMENT, "proof3", "Screenshot proof #3", false)
                .addOption(OptionType.ATTACHMENT, "proof4", "Screenshot proof #4", false)
                .addOption(OptionType.ATTACHMENT, "proof5", "Screenshot proof #5", false);

        var action = new OptionData(OptionType.STRING, "action", "Approve or reject the proof", true)
                .addChoice("Approve", "approve")
                .addChoice("Reject", "reject");

        var verify = Commands.slash("verify", "Moderator: approve or reject a proof submission")
                .addOption(OptionType.INTEGER, "submission", "Proof submission ID", true)
                .addOptions(action)
                .addOption(OptionType.STRING, "reason", "Optional moderator note/rejection reason", false);

        jda.updateCommands().addCommands(
                Commands.slash("join", "Join the Hunters & Chapaa challenge race"),
                Commands.slash("roll", "Roll the die and move on the Hunters & Chapaa board"),
                Commands.slash("challenge", "Show your current board-space challenge"),
                complete,
                Commands.slash("proof-status", "Show the status of your latest proof submission"),
                verify,
                Commands.slash("position", "Show your current board position"),
                Commands.slash("trail", "Show your verified completed spaces"),
                Commands.slash("leaderboard", "Show current player positions"),
                Commands.slash("reset", "Moderator: reset this server's game")
        ).queue();
    }
}
