package dev.palia.bot.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ChallengeRepository {
    private final Map<Integer, String> challenges;

    public ChallengeRepository() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("challenges.json")) {
            if (in == null) throw new IllegalStateException("challenges.json not found");
            Map<String, String> raw = mapper.readValue(in, new TypeReference<>() {});
            Map<Integer, String> converted = new HashMap<>();
            raw.forEach((k, v) -> converted.put(Integer.parseInt(k), v));
            this.challenges = Map.copyOf(converted);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load challenges.json", e);
        }
    }

    public String get(int space) {
        return challenges.getOrDefault(space, "No challenge configured for space " + space + ".");
    }
}
