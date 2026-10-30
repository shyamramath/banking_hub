package com.toyotafsbank.backend.plaid.dto;

import jakarta.validation.constraints.NotBlank;

public record ExchangePublicTokenRequest(
        @NotBlank(message = "publicToken is required") String publicToken,
        String institutionId,
        String institutionName
) {
}
