package com.toyotafsbank.graphqlbackend.repository;

import com.toyotafsbank.graphqlbackend.domain.Account;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface AccountRepository extends ReactiveCrudRepository<Account, Long> {
}
