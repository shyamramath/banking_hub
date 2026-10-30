package com.toyotafsbank.backend.plaid.dto;

import com.plaid.client.model.Transaction;

import java.time.LocalDate;
import java.util.List;

public record PlaidTransactionResponse(
        String itemId,
        String institutionName,
        String transactionId,
        String accountId,
        String name,
        String merchantName,
        Double amount,
        String isoCurrencyCode,
        LocalDate date,
        Boolean pending,
        List<String> category
) {
    public static PlaidTransactionResponse from(String itemId, String institutionName, Transaction transaction) {
        return new PlaidTransactionResponse(
                itemId,
                institutionName,
                transaction.getTransactionId(),
                transaction.getAccountId(),
                transaction.getName(),
                transaction.getMerchantName(),
                transaction.getAmount(),
                transaction.getIsoCurrencyCode(),
                transaction.getDate(),
                transaction.getPending(),
                transaction.getCategory()
        );
    }
}
