package com.toyotafsbank.graphqlbackend.repository;

import com.toyotafsbank.graphqlbackend.domain.PlaidItem;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface PlaidItemRepository extends ReactiveCrudRepository<PlaidItem, Long> {
    Mono<PlaidItem> findByItemId(String itemId);
}
