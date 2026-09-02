package com.cqu.greenhouse.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 演示用内存会话（最近 N 轮）。
 */
@Component
public class GreenhouseAgentSessionStore {

    public static final int MAX_TURNS = 8;

    private final ConcurrentHashMap<String, List<Turn>> sessions = new ConcurrentHashMap<>();

    public String ensureSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessions.computeIfAbsent(sessionId, id -> new ArrayList<>());
            return sessionId;
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        sessions.put(id, new ArrayList<>());
        return id;
    }

    public List<Turn> history(String sessionId) {
        return new ArrayList<>(sessions.getOrDefault(sessionId, List.of()));
    }

    public void append(String sessionId, String role, String content) {
        List<Turn> turns = sessions.computeIfAbsent(sessionId, id -> new ArrayList<>());
        synchronized (turns) {
            turns.add(new Turn(role, content));
            while (turns.size() > MAX_TURNS * 2) {
                turns.remove(0);
            }
        }
    }

    public record Turn(String role, String content) {}
}
