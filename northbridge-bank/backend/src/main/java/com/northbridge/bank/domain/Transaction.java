package com.northbridge.bank.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** amount is signed: negative = money out, positive = money in. */
public record Transaction(String id, String accountId, Instant postedAt, String description, String category,
                          BigDecimal amount, BigDecimal balanceAfter, String status) {
}
