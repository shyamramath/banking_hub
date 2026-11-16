package com.example.ensenta.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.ensenta.client.DepositorContext;
import com.example.ensenta.client.EnsentaClient.SessionResult;

/**
 * Holds the Ensenta session per signed-in user.
 * Ensenta sessions expire after 15 minutes of inactivity; each successful call refreshes them.
 * We expire ours a little earlier so we never send a dead token.
 *
 * In-memory for the sample. In production use Redis (or similar) so any gateway instance
 * can continue a user's deposit, and never log the token values.
 */
@Component
public class DepositSessionStore {

    private static final Duration IDLE_LIMIT = Duration.ofMinutes(14);

    public record Entry(DepositorContext ctx, SessionResult session, Instant lastUsed) {}

    private final Map<String, Entry> byUser = new ConcurrentHashMap<>();

    public Optional<Entry> get(String userId) {
        Entry e = byUser.get(userId);
        if (e == null || e.lastUsed().plus(IDLE_LIMIT).isBefore(Instant.now())) {
            byUser.remove(userId);
            return Optional.empty();
        }
        return Optional.of(e);
    }

    public void put(String userId, DepositorContext ctx, SessionResult session) {
        byUser.put(userId, new Entry(ctx, session, Instant.now()));
    }

    public void touch(String userId) {
        byUser.computeIfPresent(userId, (k, e) -> new Entry(e.ctx(), e.session(), Instant.now()));
    }

    public void remove(String userId) {
        byUser.remove(userId);
    }
}
