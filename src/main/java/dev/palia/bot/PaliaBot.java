package dev.palia.bot;

import dev.palia.bot.config.BotConfig;
import dev.palia.bot.db.Database;
import dev.palia.bot.db.PlayerRepository;
import dev.palia.bot.discord.CommandRegistrar;
import dev.palia.bot.discord.GameCommandListener;
import dev.palia.bot.game.BoardRepository;
import dev.palia.bot.game.ChallengeRepository;
import dev.palia.bot.game.GameService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

public class PaliaBot {
    public static void main(String[] args) throws InterruptedException {
        BotConfig config = BotConfig.load();
        Database database = new Database(config.databasePath());
        database.initialize();

        PlayerRepository players = new PlayerRepository(database);
        GameService game = new GameService(config, players, new ChallengeRepository(), new BoardRepository());

        JDA jda = JDABuilder.createDefault(config.token())
                .addEventListeners(new GameCommandListener(config, game))
                .build()
                .awaitReady();

        CommandRegistrar.register(jda);
        System.out.println("Palia Hunters & Chapaa bot is online as " + jda.getSelfUser().getName());
    }
}
