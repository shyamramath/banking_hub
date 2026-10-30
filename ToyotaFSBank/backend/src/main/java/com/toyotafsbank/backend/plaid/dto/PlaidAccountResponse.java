package com.toyotafsbank.backend.plaid.dto;

import com.plaid.client.model.AccountBase;

public record PlaidAccountResponse(
        String itemId,
        String institutionName,
        String accountId,
        String name,
        String officialName,
        String mask,
        String type,
        String subtype,
        Double availableBalance,
        Double currentBalance,
        String isoCurrencyCode
) {
    public static PlaidAccountResponse from(String itemId, String institutionName, AccountBase account) {
        return new PlaidAccountResponse(
                itemId,
                institutionName,
                account.getAccountId(),
                account.getName(),
                account.getOfficialName(),
                account.getMask(),
                account.getType() != null ? account.getType().getValue() : null,
                account.getSubtype() != null ? account.getSubtype().getValue() : null,
                account.getBalances() != null ? account.getBalances().getAvailable() : null,
                account.getBalances() != null ? account.getBalances().getCurrent() : null,
                account.getBalances() != null ? account.getBalances().getIsoCurrencyCode() : null
        );
    }
}
