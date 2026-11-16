package com.northbridge.bank.handlers.dto;

import com.northbridge.bank.domain.Account;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** Small input/output records shared by the read handlers. */
public final class Dtos {

    private Dtos() {
    }

    public static final String ACCOUNT_ID = "[a-z0-9-]{1,40}";

    public record AccountIdInput(@NotBlank @Pattern(regexp = ACCOUNT_ID) String accountId) {
    }

    public record TransactionsQuery(@NotBlank @Pattern(regexp = ACCOUNT_ID) String accountId,
                                    @Min(1) @Max(100) Integer limit) {
        public int limitOrDefault() { return limit == null ? 25 : limit; }
    }

    public record LimitInput(@Min(1) @Max(100) Integer limit) {
        public int limitOrDefault(int fallback) { return limit == null ? fallback : limit; }
    }

    /** List view: masked number only. */
    public record AccountSummary(String id, Account.Type type, String nickname, String maskedNumber,
                                 BigDecimal balance, BigDecimal availableCredit, String currency) {
        public static AccountSummary of(Account a) {
            BigDecimal available = a.creditLimit() == null ? null : a.creditLimit().add(a.balance());
            return new AccountSummary(a.id(), a.type(), a.nickname(), a.maskedNumber(), a.balance(), available,
                    a.currency());
        }
    }

    public record AccountDetails(String id, Account.Type type, String nickname, String maskedNumber,
                                 String routingNumber, BigDecimal balance, BigDecimal availableBalance,
                                 BigDecimal creditLimit, String currency, String status) {
        public static AccountDetails of(Account a) {
            BigDecimal available = a.creditLimit() == null ? a.balance() : a.creditLimit().add(a.balance());
            return new AccountDetails(a.id(), a.type(), a.nickname(), a.maskedNumber(), "111000025",
                    a.balance(), available, a.creditLimit(), a.currency(), "ACTIVE");
        }
    }
}
