package dev.gateworks.selection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Holds temporary selection sessions keyed by player UUID. */
public final class SelectionService {
    private final Map<UUID, SelectionSession> sessions = new ConcurrentHashMap<>();
    public SelectionSession get(UUID playerId) {
        return sessions.computeIfAbsent(playerId, ignored -> new SelectionSession());
    }
    public void clear(UUID playerId) { sessions.remove(playerId); }
}
