package com.northbridge.bank.handlers.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Note what is NOT here: no owner/customer id. The owner is always the authenticated caller.
 * Sending {@code "ownerId"} is rejected by the strict binder.
 */
public record TransferRequest(
        @NotBlank @Pattern(regexp = Dtos.ACCOUNT_ID) String fromAccountId,
        @Pattern(regexp = Dtos.ACCOUNT_ID) String toAccountId,
        @Pattern(regexp = "\\d{16}", message = "must be a 16-digit account number") String payeeAccountNumber,
        @Size(max = 60) @Pattern(regexp = "[\\p{L} .'-]*", message = "contains invalid characters") String payeeName,
        @NotNull @DecimalMin("0.01") @Digits(integer = 7, fraction = 2) BigDecimal amount,
        @Size(max = 140) @Pattern(regexp = "[\\p{L}\\p{N} .,'#&()/-]*", message = "contains invalid characters") String memo,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}", message = "must be a UUID") String idempotencyKey) {

    @JsonIgnore
    @AssertTrue(message = "provide exactly one of toAccountId or payeeAccountNumber")
    public boolean isExactlyOneDestination() {
        return (toAccountId == null) != (payeeAccountNumber == null);
    }

    public String maskedPayee() {
        return payeeAccountNumber == null ? null : "•••• " + payeeAccountNumber.substring(12);
    }
}
