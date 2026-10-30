package com.toyotafsbank.backend.dto;

import com.toyotafsbank.backend.model.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AccountRequest(
        @NotBlank(message = "ownerName is required") String ownerName,
        @NotNull(message = "accountType is required") AccountType accountType,
        @NotNull(message = "balance is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "balance cannot be negative")
        BigDecimal balance
) {
}
