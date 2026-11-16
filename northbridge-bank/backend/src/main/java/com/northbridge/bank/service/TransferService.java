package com.northbridge.bank.service;

import com.northbridge.bank.api.ApiException;
import com.northbridge.bank.config.BankProperties;
import com.northbridge.bank.domain.Account;
import com.northbridge.bank.domain.BankStore;
import com.northbridge.bank.domain.Transaction;
import com.northbridge.bank.handlers.dto.TransferReceipt;
import com.northbridge.bank.handlers.dto.TransferRequest;
import com.northbridge.bank.security.CurrentUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TransferService {

    private final BankStore store;
    private final AccountService accounts;
    private final BigDecimal maxAmount;
    /** (userId|idempotencyKey) -> receipt. Production: a DB table with a unique constraint. */
    private final Map<String, TransferReceipt> idempotency = new ConcurrentHashMap<>();

    public TransferService(BankStore store, AccountService accounts, BankProperties props) {
        this.store = store;
        this.accounts = accounts;
        this.maxAmount = props.limits().maxTransferAmount();
    }

    public TransferReceipt transfer(CurrentUser user, TransferRequest req) {
        String idemKey = user.id() + '|' + req.idempotencyKey();
        TransferReceipt previous = idempotency.get(idemKey);
        if (previous != null) {
            return previous.asReplay();
        }

        // Object-level check: the source must belong to the caller.
        Account from = accounts.ownedAccount(user, req.fromAccountId());

        if (from.type() == Account.Type.CREDIT) {
            throw ApiException.businessRule("SOURCE_NOT_ALLOWED", "Transfers from a credit card are not supported");
        }
        if (req.amount().compareTo(maxAmount) > 0) {
            throw ApiException.businessRule("LIMIT_EXCEEDED",
                    "Online transfers are limited to " + maxAmount.toPlainString() + " per transaction");
        }

        return store.locked(() -> {
            TransferReceipt raced = idempotency.get(idemKey);
            if (raced != null) return raced.asReplay();

            Account to = resolveDestination(user, req);
            if (to.id().equals(from.id())) {
                throw ApiException.businessRule("SAME_ACCOUNT", "Choose a different destination account");
            }
            if (from.balance().compareTo(req.amount()) < 0) {
                throw ApiException.businessRule("INSUFFICIENT_FUNDS", "Insufficient available balance");
            }

            boolean ownTransfer = to.ownerId().equals(user.id());
            String memo = req.memo() == null || req.memo().isBlank() ? "" : " - " + req.memo().strip();
            String toLabel = ownTransfer ? to.nickname() : (req.payeeName() == null ? "payee" : req.payeeName().strip());

            Transaction debit = store.post(from, req.amount().negate(), "Transfer to " + toLabel + memo, "Transfer");
            store.post(to, req.amount(), "Transfer from " + user.displayName() + memo, "Transfer");

            TransferReceipt receipt = new TransferReceipt(
                    "TRF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                    "COMPLETED", req.amount(), from.currency(),
                    from.maskedNumber(), to.maskedNumber(), toLabel,
                    debit.balanceAfter(), Instant.now(), false);
            idempotency.put(idemKey, receipt);
            return receipt;
        });
    }

    private Account resolveDestination(CurrentUser user, TransferRequest req) {
        if (req.toAccountId() != null) {
            // Own-account transfer: must also be owned by the caller.
            return accounts.ownedAccount(user, req.toAccountId());
        }
        // External payee by account number. In this demo only Northbridge numbers are reachable.
        return store.accountByNumber(req.payeeAccountNumber())
                .filter(a -> a.type() != Account.Type.CREDIT)
                .orElseThrow(() -> ApiException.businessRule("PAYEE_NOT_FOUND",
                        "We couldn't find an eligible account with that number"));
    }
}
