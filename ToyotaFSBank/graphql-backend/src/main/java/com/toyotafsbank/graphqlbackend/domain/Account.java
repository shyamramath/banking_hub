package com.toyotafsbank.graphqlbackend.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("accounts")
public record Account(
        @Id Long id,
        String accountNumber,
        String ownerName,
        AccountType accountType,
        double balance,
        Instant createdAt,
        Instant updatedAt
) {
}
