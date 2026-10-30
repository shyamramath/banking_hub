package com.toyotafsbank.graphqlbackend.graphql.dto;

import com.toyotafsbank.graphqlbackend.domain.PlaidItem;

public record LinkedItemPayload(
        String itemId,
        String institutionId,
        String institutionName,
        String linkedAt
) {
    public static LinkedItemPayload from(PlaidItem item) {
        return new LinkedItemPayload(
                item.itemId(),
                item.institutionId(),
                item.institutionName(),
                item.linkedAt().toString()
        );
    }
}
