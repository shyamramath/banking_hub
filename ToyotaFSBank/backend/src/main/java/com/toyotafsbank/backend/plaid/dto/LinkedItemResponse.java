package com.toyotafsbank.backend.plaid.dto;

import com.toyotafsbank.backend.plaid.model.PlaidItem;

import java.time.Instant;

public record LinkedItemResponse(
        String itemId,
        String institutionId,
        String institutionName,
        Instant linkedAt
) {
    public static LinkedItemResponse from(PlaidItem item) {
        return new LinkedItemResponse(
                item.getItemId(),
                item.getInstitutionId(),
                item.getInstitutionName(),
                item.getLinkedAt()
        );
    }
}
