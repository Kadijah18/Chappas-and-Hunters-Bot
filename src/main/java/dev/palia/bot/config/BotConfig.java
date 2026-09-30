package dev.palia.bot.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public record BotConfig(
        String token,
        String gameChannelId,
        boolean playerThreadsEnabled,
        int startSpace,
        int finalSpace,
        int dieSides,
        boolean completedSpaceIsFree,
        int maxProofAttachments,
        boolean proofRequired,
        String databasePath
) {
    public static BotConfig load() {
        Properties props = new Properties();
        try (InputStream in = BotConfig.class.getClassLoader().getResourceAsStream("bot.properties")) {
            if (in != null) props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read bot.properties", e);
        }

        String token = System.getenv("PALIA_BOT_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("PALIA_BOT_TOKEN environment variable is required.");
        }

        String db = System.getenv().getOrDefault("PALIA_BOT_DB", "palia-hunters-chapaa.db");
        return new BotConfig(
                token,
                props.getProperty("game.channel.id", "").trim(),
                Boolean.parseBoolean(props.getProperty("player.threads.enabled", "true")),
                Integer.parseInt(props.getProperty("board.start.space", "1")),
                Integer.parseInt(props.getProperty("board.final.space", "90")),
                Integer.parseInt(props.getProperty("board.die.sides", "6")),
                Boolean.parseBoolean(props.getProperty("completed.space.is.free", "true")),
                Integer.parseInt(props.getProperty("proof.max.attachments", "5")),
                Boolean.parseBoolean(props.getProperty("proof.required", "true")),
                db
        );
    }
}
