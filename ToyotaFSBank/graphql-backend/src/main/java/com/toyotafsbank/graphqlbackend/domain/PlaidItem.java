package com.toyotafsbank.graphqlbackend.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("plaid_items")
public record PlaidItem(
        @Id Long id,
        String itemId,
        String accessToken,
        String institutionId,
        String institutionName,
        Instant linkedAt
) {
}
