package com.toyotafsbank.backend.plaid.repository;

import com.toyotafsbank.backend.plaid.model.PlaidItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlaidItemRepository extends JpaRepository<PlaidItem, Long> {
    Optional<PlaidItem> findByItemId(String itemId);
}
