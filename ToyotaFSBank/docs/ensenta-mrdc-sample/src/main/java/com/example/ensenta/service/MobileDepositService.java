package com.example.ensenta.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.ensenta.client.DepositorContext;
import com.example.ensenta.client.EnsentaClient;
import com.example.ensenta.client.EnsentaClient.AccountHolder;
import com.example.ensenta.client.EnsentaClient.BatchSummary;
import com.example.ensenta.client.EnsentaClient.DepositItem;
import com.example.ensenta.client.EnsentaClient.PostResult;
import com.example.ensenta.client.EnsentaClient.SessionResult;
import com.example.ensenta.client.EnsentaException;
import com.example.ensenta.config.EnsentaProperties;

/**
 * Orchestrates the Ensenta multi-check workflow for the mobile app.
 *
 * Two-step UX, matching Ensenta's "conversational" API:
 *   1. submit()  -> create batch, upload every check, wait for processing, return items + risk factors
 *   2. confirm() -> user fixes/confirms/deletes flagged items, then the batch is posted
 */
@Service
public class MobileDepositService {

    private final EnsentaClient ensenta;
    private final DepositSessionStore sessions;
    private final EligibilityService eligibility;
    private final EnsentaProperties props;

    public MobileDepositService(EnsentaClient ensenta, DepositSessionStore sessions,
                                EligibilityService eligibility, EnsentaProperties props) {
        this.ensenta = ensenta;
        this.sessions = sessions;
        this.eligibility = eligibility;
        this.props = props;
    }

    public record CheckImage(BigDecimal amount, String frontBase64, String backBase64) {}
    public record ReviewResult(long batchId, BatchSummary summary, List<DepositItem> items) {}

    // ------------------------------------------------------------------ step 1

    public ReviewResult submit(String userId, DepositorContext device, String toAccountNumber,
                               String description, List<CheckImage> checks) {
        // Ensenta leaves enrollment to us: only eligible users and accounts reach StartSession.
        AccountHolder holder = eligibility.eligibleHolder(userId);

        DepositorContext ctx = sessionFor(userId, device, holder);
        int accountIndex = indexOf(userId, toAccountNumber);

        long batchId = withSessionRetry(userId, device, holder,
                c -> ensenta.createDepositBatch(c, accountIndex, checks.size(), description));
        ctx = sessions.get(userId).orElseThrow().ctx();

        for (int i = 0; i < checks.size(); i++) {
            CheckImage chk = checks.get(i);
            ensenta.addDepositItem(ctx, batchId, i + 1, chk.amount(), chk.frontBase64(), chk.backBase64());
            sessions.touch(userId);
        }

        BatchSummary summary = waitForItems(ctx, batchId, checks.size());

        List<DepositItem> items = new ArrayList<>();
        for (Integer n : summary.itemNumbers()) {
            items.add(ensenta.getDepositItem(ctx, batchId, n));
        }
        sessions.touch(userId);
        return new ReviewResult(batchId, summary, items);
    }

    // ------------------------------------------------------------------ step 2

    public record ItemDecision(int itemNumber, boolean delete, boolean confirm, BigDecimal correctedAmount) {}

    public PostResult confirm(String userId, long batchId, List<ItemDecision> decisions, List<Integer> itemsToPost) {
        DepositorContext ctx = sessions.get(userId)
                .orElseThrow(() -> new EnsentaException("E51", "Session expired; restart the deposit"))
                .ctx();

        for (ItemDecision d : decisions) {
            ensenta.updateDepositItem(ctx, batchId, d.itemNumber(),
                    d.delete() ? Boolean.TRUE : null,
                    d.confirm() ? Boolean.TRUE : null,
                    d.correctedAmount());
        }
        PostResult result = ensenta.postDepositBatch(ctx, batchId, itemsToPost);   // E35 if reject-level risk remains
        sessions.touch(userId);
        return result;
    }

    public void finish(String userId) {
        sessions.get(userId).ifPresent(e -> ensenta.endSession(e.ctx()));
        sessions.remove(userId);
    }

    // ------------------------------------------------------------------ internals

    /** Docs: if ItemCount does not match yet, wait ~2-5 s and call GetDepositBatchSummary again. */
    private BatchSummary waitForItems(DepositorContext ctx, long batchId, int expected) {
        var poll = props.batchSummaryPoll();
        long delay = poll.initialDelayMs();
        for (int attempt = 1; attempt <= poll.maxAttempts(); attempt++) {
            BatchSummary s = ensenta.getDepositBatchSummary(ctx, batchId);
            if (s.itemCount() == expected) return s;
            sleep(delay);
            delay = Math.min(delay * 2, poll.maxDelayMs());
        }
        throw new EnsentaException("E88", "Items not processed in time; ask the user to retry");
    }

    private DepositorContext sessionFor(String userId, DepositorContext device, AccountHolder holder) {
        return sessions.get(userId).map(DepositSessionStore.Entry::ctx).orElseGet(() -> {
            SessionResult s = ensenta.startSession(device, holder);
            DepositorContext ctx = device.withSession(s.sessionToken(), s.sessionStateId());
            sessions.put(userId, ctx, s);
            return ctx;
        });
    }

    private int indexOf(String userId, String accountNumber) {
        return sessions.get(userId).orElseThrow().session().accountKeys().stream()
                .filter(k -> k.accountNumber().equals(accountNumber))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Account not eligible for mobile deposit"))
                .accountNumberIndex();
    }

    /** On E50/E51 (session missing or expired) start a fresh session once and retry. */
    private <T> T withSessionRetry(String userId, DepositorContext device, AccountHolder holder,
                                   java.util.function.Function<DepositorContext, T> call) {
        DepositorContext ctx = sessions.get(userId).orElseThrow().ctx();
        try {
            return call.apply(ctx);
        } catch (EnsentaException e) {
            if (e.code().action != EnsentaException.Action.RESTART_SESSION) throw e;
            sessions.remove(userId);
            return call.apply(sessionFor(userId, device, holder));
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
