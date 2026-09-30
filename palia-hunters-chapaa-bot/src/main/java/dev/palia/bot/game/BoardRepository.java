package dev.palia.bot.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class BoardRepository {
    private final Map<Integer, Integer> movement;

    public BoardRepository() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("board-movement.json")) {
            if (in == null) throw new IllegalStateException("board-movement.json not found");
            Map<String, Integer> raw = mapper.readValue(in, new TypeReference<>() {});
            Map<Integer, Integer> converted = new HashMap<>();
            raw.forEach((k, v) -> converted.put(Integer.parseInt(k), v));
            this.movement = Map.copyOf(converted);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load board-movement.json", e);
        }
    }

    public int resolve(int landingSpace) {
        return movement.getOrDefault(landingSpace, landingSpace);
    }

    public boolean isSpecial(int landingSpace) {
        return movement.containsKey(landingSpace);
    }
}
