package com.northbridge.bank.dispatch;

import com.northbridge.bank.api.ApiException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Fixed-window limiter keyed by (user, operation). Gateways only see one URL, so limits
 * have to be applied here (and at the BFF) using the operation name.
 *
 * <p>In-memory for the demo. With more than one instance use Redis / Bucket4j so the
 * window is shared.
 */
@Component
public class OperationRateLimiter {

    private record Window(long minute, AtomicInteger count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public OperationRateLimiter() {
        this(Clock.systemUTC());
    }

    OperationRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void acquire(String userId, String operation, int perMinute) {
        long minute = clock.millis() / 60_000;
        Window window = windows.compute(userId + '|' + operation,
                (key, old) -> old == null || old.minute() != minute ? new Window(minute, new AtomicInteger()) : old);
        if (window.count().incrementAndGet() > perMinute) {
            throw ApiException.rateLimited(operation);
        }
        if (windows.size() > 50_000) {
            windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
        }
    }

    /** Test helper. */
    public void reset() {
        windows.clear();
    }
}
