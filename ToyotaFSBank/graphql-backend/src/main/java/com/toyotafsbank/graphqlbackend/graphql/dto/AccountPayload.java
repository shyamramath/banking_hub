package com.toyotafsbank.graphqlbackend.graphql.dto;

import com.toyotafsbank.graphqlbackend.domain.Account;
import com.toyotafsbank.graphqlbackend.domain.AccountType;

public record AccountPayload(
        Long id,
        String accountNumber,
        String ownerName,
        AccountType accountType,
        double balance,
        String createdAt,
        String updatedAt
) {
    public static AccountPayload from(Account account) {
        return new AccountPayload(
                account.id(),
                account.accountNumber(),
                account.ownerName(),
                account.accountType(),
                account.balance(),
                account.createdAt().toString(),
                account.updatedAt().toString()
        );
    }
}
