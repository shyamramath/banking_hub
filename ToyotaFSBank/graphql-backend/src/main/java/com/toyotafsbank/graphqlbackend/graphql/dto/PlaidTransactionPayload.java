package com.toyotafsbank.graphqlbackend.graphql.dto;

import com.plaid.client.model.Transaction;

import java.util.List;

public record PlaidTransactionPayload(
        String itemId,
        String institutionName,
        String transactionId,
        String accountId,
        String name,
        String merchantName,
        Double amount,
        String isoCurrencyCode,
        String date,
        Boolean pending,
        List<String> category
) {
    public static PlaidTransactionPayload from(String itemId, String institutionName, Transaction transaction) {
        return new PlaidTransactionPayload(
                itemId,
                institutionName,
                transaction.getTransactionId(),
                transaction.getAccountId(),
                transaction.getName(),
                transaction.getMerchantName(),
                transaction.getAmount(),
                transaction.getIsoCurrencyCode(),
                transaction.getDate() != null ? transaction.getDate().toString() : null,
                transaction.getPending(),
                transaction.getCategory()
        );
    }
}
