package com.northbridge.bank.handlers.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferReceipt(String transferId, String status, BigDecimal amount, String currency,
                              String fromAccount, String toAccount, String toLabel, BigDecimal newBalance,
                              Instant completedAt, boolean replayed) {

    /** Same receipt, flagged so the UI can say "already processed" instead of moving money twice. */
    public TransferReceipt asReplay() {
        return new TransferReceipt(transferId, status, amount, currency, fromAccount, toAccount, toLabel,
                newBalance, completedAt, true);
    }
}
