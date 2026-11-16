package com.northbridge.bank.domain;

import java.math.BigDecimal;

/** Mutable balance; all mutation happens under {@link BankStore}'s lock. */
public final class Account {

    public enum Type { CHECKING, SAVINGS, CREDIT }

    private final String id;
    private final String ownerId;
    private final Type type;
    private final String nickname;
    private final String number;
    private final String currency;
    private BigDecimal balance;
    private final BigDecimal creditLimit;

    public Account(String id, String ownerId, Type type, String nickname, String number, String currency,
                   BigDecimal balance, BigDecimal creditLimit) {
        this.id = id;
        this.ownerId = ownerId;
        this.type = type;
        this.nickname = nickname;
        this.number = number;
        this.currency = currency;
        this.balance = balance;
        this.creditLimit = creditLimit;
    }

    public String id() { return id; }
    public String ownerId() { return ownerId; }
    public Type type() { return type; }
    public String nickname() { return nickname; }
    public String number() { return number; }
    public String currency() { return currency; }
    public BigDecimal balance() { return balance; }
    public BigDecimal creditLimit() { return creditLimit; }

    public String maskedNumber() {
        return "•••• " + number.substring(number.length() - 4);
    }

    void apply(BigDecimal signedAmount) {
        this.balance = this.balance.add(signedAmount);
    }
}
