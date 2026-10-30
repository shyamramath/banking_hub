package com.toyotafsbank.graphqlbackend.graphql.dto;

import com.toyotafsbank.graphqlbackend.domain.AccountType;

public record AccountInput(String ownerName, AccountType accountType, double balance) {
}
