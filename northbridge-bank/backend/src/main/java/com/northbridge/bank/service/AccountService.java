package com.northbridge.bank.service;

import com.northbridge.bank.api.ApiException;
import com.northbridge.bank.domain.Account;
import com.northbridge.bank.domain.BankStore;
import com.northbridge.bank.domain.CustomerProfile;
import com.northbridge.bank.domain.Transaction;
import com.northbridge.bank.security.CurrentUser;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Object-level authorization lives HERE, next to the data: every lookup is scoped to the
 * caller's id from the JWT. An account that exists but belongs to someone else is reported as
 * "not found", exactly like one that doesn't exist (no BOLA / IDOR probing).
 */
@Service
public class AccountService {

    private final BankStore store;

    public AccountService(BankStore store) {
        this.store = store;
    }

    public CustomerProfile profile(CurrentUser user) {
        return store.profile(user.id()).orElseThrow(() -> ApiException.notFound("Profile"));
    }

    public List<Account> accounts(CurrentUser user) {
        return store.accountsOf(user.id());
    }

    public Account ownedAccount(CurrentUser user, String accountId) {
        return store.account(accountId)
                .filter(a -> a.ownerId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("Account"));
    }

    public List<Transaction> transactions(CurrentUser user, String accountId, int limit) {
        Account account = ownedAccount(user, accountId);
        return store.transactionsOf(List.of(account.id()), limit);
    }

    public List<Transaction> recentActivity(CurrentUser user, int limit) {
        List<String> mine = store.accountsOf(user.id()).stream().map(Account::id).toList();
        return store.transactionsOf(mine, limit);
    }
}
