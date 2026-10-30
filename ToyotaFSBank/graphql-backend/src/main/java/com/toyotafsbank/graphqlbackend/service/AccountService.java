package com.toyotafsbank.graphqlbackend.service;

import com.toyotafsbank.graphqlbackend.domain.Account;
import com.toyotafsbank.graphqlbackend.domain.AccountType;
import com.toyotafsbank.graphqlbackend.exception.ResourceNotFoundException;
import com.toyotafsbank.graphqlbackend.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    public Flux<Account> findAll() {
        return accountRepository.findAll();
    }

    public Mono<Account> findById(Long id) {
        return accountRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Account not found with id: " + id)));
    }

    public Mono<Account> create(String ownerName, AccountType accountType, double balance) {
        Account account = new Account(
                null,
                generateAccountNumber(),
                ownerName,
                accountType,
                balance,
                Instant.now(),
                Instant.now()
        );
        return accountRepository.save(account);
    }

    public Mono<Account> update(Long id, String ownerName, AccountType accountType, double balance) {
        return findById(id)
                .map(existing -> new Account(
                        existing.id(),
                        existing.accountNumber(),
                        ownerName,
                        accountType,
                        balance,
                        existing.createdAt(),
                        Instant.now()))
                .flatMap(accountRepository::save);
    }

    public Mono<Boolean> delete(Long id) {
        return findById(id)
                .flatMap(account -> accountRepository.delete(account).thenReturn(true));
    }

    private String generateAccountNumber() {
        return "TFS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
