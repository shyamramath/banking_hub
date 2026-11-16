package com.northbridge.bank.handlers;

import com.northbridge.bank.domain.CustomerProfile;
import com.northbridge.bank.domain.Transaction;
import com.northbridge.bank.dispatch.BankOperation;
import com.northbridge.bank.dispatch.NoInput;
import com.northbridge.bank.dispatch.OperationContext;
import com.northbridge.bank.dispatch.OperationHandler;
import com.northbridge.bank.handlers.dto.Dtos.AccountDetails;
import com.northbridge.bank.handlers.dto.Dtos.AccountIdInput;
import com.northbridge.bank.handlers.dto.Dtos.AccountSummary;
import com.northbridge.bank.handlers.dto.Dtos.LimitInput;
import com.northbridge.bank.handlers.dto.Dtos.TransactionsQuery;
import com.northbridge.bank.service.AccountService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Read-only operations. Each nested class is a separate Spring bean / operation.
 * They are grouped in one file only to keep the sample compact.
 */
public final class ReadHandlers {

    private ReadHandlers() {
    }

    @Component
    @BankOperation(name = "profile.get", scopes = "accounts:read", anyRole = {"customer", "auditor"})
    public static class GetProfile implements OperationHandler<NoInput, CustomerProfile> {
        private final AccountService accounts;
        public GetProfile(AccountService accounts) { this.accounts = accounts; }
        public Class<NoInput> inputType() { return NoInput.class; }
        public CustomerProfile handle(NoInput in, OperationContext ctx) { return accounts.profile(ctx.user()); }
    }

    @Component
    @BankOperation(name = "accounts.list", scopes = "accounts:read", anyRole = "customer")
    public static class ListAccounts implements OperationHandler<NoInput, List<AccountSummary>> {
        private final AccountService accounts;
        public ListAccounts(AccountService accounts) { this.accounts = accounts; }
        public Class<NoInput> inputType() { return NoInput.class; }
        public List<AccountSummary> handle(NoInput in, OperationContext ctx) {
            return accounts.accounts(ctx.user()).stream().map(AccountSummary::of).toList();
        }
    }

    @Component
    @BankOperation(name = "account.details", scopes = "accounts:read", anyRole = "customer")
    public static class AccountDetailsHandler implements OperationHandler<AccountIdInput, AccountDetails> {
        private final AccountService accounts;
        public AccountDetailsHandler(AccountService accounts) { this.accounts = accounts; }
        public Class<AccountIdInput> inputType() { return AccountIdInput.class; }
        public AccountDetails handle(AccountIdInput in, OperationContext ctx) {
            return AccountDetails.of(accounts.ownedAccount(ctx.user(), in.accountId()));
        }
    }

    @Component
    @BankOperation(name = "transactions.list", scopes = "accounts:read", anyRole = "customer")
    public static class ListTransactions implements OperationHandler<TransactionsQuery, List<Transaction>> {
        private final AccountService accounts;
        public ListTransactions(AccountService accounts) { this.accounts = accounts; }
        public Class<TransactionsQuery> inputType() { return TransactionsQuery.class; }
        public List<Transaction> handle(TransactionsQuery in, OperationContext ctx) {
            return accounts.transactions(ctx.user(), in.accountId(), in.limitOrDefault());
        }
    }

    @Component
    @BankOperation(name = "transactions.recent", scopes = "accounts:read", anyRole = "customer")
    public static class RecentActivity implements OperationHandler<LimitInput, List<Transaction>> {
        private final AccountService accounts;
        public RecentActivity(AccountService accounts) { this.accounts = accounts; }
        public Class<LimitInput> inputType() { return LimitInput.class; }
        public List<Transaction> handle(LimitInput in, OperationContext ctx) {
            return accounts.recentActivity(ctx.user(), in.limitOrDefault(8));
        }
    }
}
