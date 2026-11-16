package com.northbridge.bank.audit;

import com.northbridge.bank.security.CurrentUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Append-only audit trail of who did what, through which operation, with what outcome.
 * Demo: kept in memory and written to the "AUDIT" logger. Production: ship the AUDIT logger to
 * a WORM store / SIEM (PCI DSS Req. 10).
 */
@Service
public class AuditService {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");
    private static final int MAX_ENTRIES = 500;

    public record AuditEntry(String id, Instant at, String userId, String username, String operation,
                             String outcome, String correlationId, String detail) {
    }

    private final Deque<AuditEntry> entries = new ConcurrentLinkedDeque<>();

    public void record(CurrentUser user, String operation, String outcome, String correlationId, String detail) {
        AuditEntry entry = new AuditEntry(UUID.randomUUID().toString(), Instant.now(), user.id(), user.username(),
                operation, outcome, correlationId, detail == null ? "" : detail);
        entries.addFirst(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.pollLast();
        }
        AUDIT.info("op={} outcome={} user={} sub={} cid={} detail=\"{}\"",
                operation, outcome, user.username(), user.id(), correlationId, entry.detail());
    }

    public List<AuditEntry> latest(int limit) {
        List<AuditEntry> out = new ArrayList<>(limit);
        for (AuditEntry e : entries) {
            if (out.size() >= limit) break;
            out.add(e);
        }
        return out;
    }
}
