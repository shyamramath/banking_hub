package com.toyotafsbank.backend.service;

import com.toyotafsbank.backend.dto.AccountRequest;
import com.toyotafsbank.backend.exception.ResourceNotFoundException;
import com.toyotafsbank.backend.model.Account;
import com.toyotafsbank.backend.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    public List<Account> findAll() {
        return accountRepository.findAll();
    }

    public Account findById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id));
    }

    public Account create(AccountRequest request) {
        Account account = Account.builder()
                .accountNumber(generateAccountNumber())
                .ownerName(request.ownerName())
                .accountType(request.accountType())
                .balance(request.balance())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return accountRepository.save(account);
    }

    public Account update(Long id, AccountRequest request) {
        Account existing = findById(id);
        existing.setOwnerName(request.ownerName());
        existing.setAccountType(request.accountType());
        existing.setBalance(request.balance());
        existing.setUpdatedAt(Instant.now());
        return accountRepository.save(existing);
    }

    public void delete(Long id) {
        Account existing = findById(id);
        accountRepository.delete(existing);
    }

    private String generateAccountNumber() {
        return "TFS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
