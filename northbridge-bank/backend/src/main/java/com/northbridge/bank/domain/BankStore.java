package com.northbridge.bank.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * In-memory stand-in for the core-banking system, seeded with demo data.
 * User ids match the fixed Keycloak user ids in keycloak/northbridge-realm.json.
 */
@Component
public class BankStore {

    public static final String ALICE = "11111111-1111-4111-8111-111111111111";
    public static final String BOB = "22222222-2222-4222-8222-222222222222";
    public static final String CAROL = "33333333-3333-4333-8333-333333333333";

    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private final Map<String, CustomerProfile> profiles = new LinkedHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public BankStore() {
        seed();
    }

    // ---- queries ----------------------------------------------------------------------------

    public Optional<CustomerProfile> profile(String userId) {
        return Optional.ofNullable(profiles.get(userId));
    }

    public List<Account> accountsOf(String ownerId) {
        return locked(() -> accounts.values().stream().filter(a -> a.ownerId().equals(ownerId)).toList());
    }

    public Optional<Account> account(String accountId) {
        return Optional.ofNullable(accounts.get(accountId));
    }

    public Optional<Account> accountByNumber(String number) {
        return accounts.values().stream().filter(a -> a.number().equals(number)).findFirst();
    }

    public List<Transaction> transactionsOf(List<String> accountIds, int limit) {
        return locked(() -> transactions.stream()
                .filter(t -> accountIds.contains(t.accountId()))
                .sorted(Comparator.comparing(Transaction::postedAt).reversed())
                .limit(limit)
                .toList());
    }

    // ---- commands ---------------------------------------------------------------------------

    public <T> T locked(Supplier<T> work) {
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    /** Must be called inside {@link #locked}. */
    public Transaction post(Account account, BigDecimal signedAmount, String description, String category) {
        if (!lock.isHeldByCurrentThread()) throw new IllegalStateException("post() requires the store lock");
        account.apply(signedAmount);
        Transaction tx = new Transaction(UUID.randomUUID().toString(), account.id(), Instant.now(), description,
                category, signedAmount, account.balance(), "POSTED");
        transactions.add(tx);
        return tx;
    }

    // ---- seed data --------------------------------------------------------------------------

    private void seed() {
        profiles.put(ALICE, new CustomerProfile(ALICE, "Alice Carter", "Premier", LocalDate.of(2016, 4, 12),
                "a••••@example.com", "Jordan Ellis"));
        profiles.put(BOB, new CustomerProfile(BOB, "Bob Nguyen", "Everyday", LocalDate.of(2021, 9, 3),
                "b••@example.com", null));
        profiles.put(CAROL, new CustomerProfile(CAROL, "Carol Diaz", "Staff - Internal Audit", LocalDate.of(2019, 1, 7),
                "c••••@example.com", null));

        addAccount("acc-alice-chk", ALICE, Account.Type.CHECKING, "Everyday Checking", "4021003318821234", "8420.55", null);
        addAccount("acc-alice-sav", ALICE, Account.Type.SAVINGS, "High-Yield Savings", "4021003318825678", "25310.00", null);
        addAccount("acc-alice-cc", ALICE, Account.Type.CREDIT, "Northbridge Rewards Card", "5500004410029012", "-1245.32", "12000.00");
        addAccount("acc-bob-chk", BOB, Account.Type.CHECKING, "Checking", "4021009911214455", "1980.40", null);
        addAccount("acc-bob-sav", BOB, Account.Type.SAVINGS, "Rainy Day Fund", "4021009911217788", "6400.00", null);

        Instant now = Instant.now();
        seedTx("acc-alice-chk", now, 1, "Payroll - Acme Corp", "Income", "4250.00");
        seedTx("acc-alice-chk", now, 2, "Whole Foods Market", "Groceries", "-142.87");
        seedTx("acc-alice-chk", now, 3, "Austin Energy", "Utilities", "-118.20");
        seedTx("acc-alice-chk", now, 4, "Transfer to Savings", "Transfer", "-500.00");
        seedTx("acc-alice-chk", now, 6, "Netflix", "Entertainment", "-15.49");
        seedTx("acc-alice-chk", now, 8, "Shell Oil", "Transport", "-52.30");
        seedTx("acc-alice-chk", now, 9, "Rent - Lakeline Apartments", "Housing", "-1850.00");
        seedTx("acc-alice-sav", now, 4, "Transfer from Checking", "Transfer", "500.00");
        seedTx("acc-alice-sav", now, 30, "Interest payment", "Interest", "88.12");
        seedTx("acc-alice-cc", now, 1, "Delta Air Lines", "Travel", "-612.40");
        seedTx("acc-alice-cc", now, 3, "Uchi Austin", "Dining", "-186.55");
        seedTx("acc-alice-cc", now, 5, "Amazon.com", "Shopping", "-89.99");
        seedTx("acc-alice-cc", now, 12, "Card payment - thank you", "Payment", "1500.00");
        seedTx("acc-bob-chk", now, 2, "Payroll - Initech", "Income", "2100.00");
        seedTx("acc-bob-chk", now, 3, "H-E-B", "Groceries", "-96.15");
        seedTx("acc-bob-chk", now, 7, "Spotify", "Entertainment", "-11.99");
        seedTx("acc-bob-sav", now, 15, "Transfer from Checking", "Transfer", "250.00");
        backfillRunningBalances();
    }

    /** Seeded rows were created without a running balance; derive it backwards from today's balance. */
    private void backfillRunningBalances() {
        List<Transaction> fixed = new ArrayList<>();
        for (Account a : accounts.values()) {
            BigDecimal running = a.balance();
            List<Transaction> mine = transactions.stream()
                    .filter(t -> t.accountId().equals(a.id()))
                    .sorted(Comparator.comparing(Transaction::postedAt).reversed())
                    .toList();
            for (Transaction t : mine) {
                fixed.add(new Transaction(t.id(), t.accountId(), t.postedAt(), t.description(), t.category(),
                        t.amount(), running, t.status()));
                running = running.subtract(t.amount());
            }
        }
        transactions.clear();
        transactions.addAll(fixed);
    }

    private void addAccount(String id, String owner, Account.Type type, String nickname, String number,
                            String balance, String creditLimit) {
        accounts.put(id, new Account(id, owner, type, nickname, number, "USD", new BigDecimal(balance),
                creditLimit == null ? null : new BigDecimal(creditLimit)));
    }

    private void seedTx(String accountId, Instant now, int daysAgo, String description, String category, String amount) {
        Account a = accounts.get(accountId);
        transactions.add(new Transaction(UUID.randomUUID().toString(), accountId,
                now.minus(daysAgo, ChronoUnit.DAYS).minus(daysAgo * 37L, ChronoUnit.MINUTES),
                description, category, new BigDecimal(amount), a.balance(), "POSTED"));
    }
}
