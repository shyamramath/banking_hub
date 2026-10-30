package com.toyotafsbank.graphqlbackend.service;

import com.plaid.client.model.AccountBase;
import com.plaid.client.model.AccountsBalanceGetRequest;
import com.plaid.client.model.AccountsGetResponse;
import com.plaid.client.model.CountryCode;
import com.plaid.client.model.ItemPublicTokenExchangeRequest;
import com.plaid.client.model.ItemPublicTokenExchangeResponse;
import com.plaid.client.model.ItemRemoveRequest;
import com.plaid.client.model.LinkTokenCreateRequest;
import com.plaid.client.model.LinkTokenCreateRequestUser;
import com.plaid.client.model.LinkTokenCreateResponse;
import com.plaid.client.model.Products;
import com.plaid.client.model.Transaction;
import com.plaid.client.model.TransactionsSyncRequest;
import com.plaid.client.model.TransactionsSyncResponse;
import com.plaid.client.request.PlaidApi;
import com.toyotafsbank.graphqlbackend.config.PlaidProperties;
import com.toyotafsbank.graphqlbackend.domain.PlaidItem;
import com.toyotafsbank.graphqlbackend.exception.PlaidApiException;
import com.toyotafsbank.graphqlbackend.exception.ResourceNotFoundException;
import com.toyotafsbank.graphqlbackend.graphql.dto.PlaidAccountPayload;
import com.toyotafsbank.graphqlbackend.graphql.dto.PlaidTransactionPayload;
import com.toyotafsbank.graphqlbackend.repository.PlaidItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Plaid Java SDK is a blocking (Retrofit2) client with no reactive
 * variant, so every call here runs on the boundedElastic scheduler via
 * Mono.fromCallable - that keeps the blocking HTTP call off Netty's event
 * loop threads while still exposing a fully reactive Mono/Flux API to the
 * GraphQL layer.
 */
@Service
@RequiredArgsConstructor
public class PlaidService {

    private final PlaidApi plaidApi;
    private final PlaidProperties plaidProperties;
    private final PlaidItemRepository plaidItemRepository;

    public Mono<String> createLinkToken() {
        return Mono.fromCallable(this::doCreateLinkToken)
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<PlaidItem> exchangePublicToken(String publicToken, String institutionId, String institutionName) {
        return Mono.fromCallable(() -> doExchangePublicToken(publicToken))
                .subscribeOn(Schedulers.boundedElastic())
                .map(response -> new PlaidItem(
                        null,
                        response.getItemId(),
                        response.getAccessToken(),
                        institutionId,
                        institutionName,
                        Instant.now()))
                .flatMap(plaidItemRepository::save);
    }

    public Flux<PlaidItem> listLinkedItems() {
        return plaidItemRepository.findAll();
    }

    public Flux<PlaidAccountPayload> getAllAccounts() {
        return plaidItemRepository.findAll()
                .flatMap(item -> Mono.fromCallable(() -> doGetAccounts(item))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable));
    }

    public Flux<PlaidTransactionPayload> getAllTransactions() {
        return plaidItemRepository.findAll()
                .flatMap(item -> Mono.fromCallable(() -> doGetTransactions(item))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable))
                .sort(Comparator.comparing(PlaidTransactionPayload::date).reversed());
    }

    public Mono<Boolean> unlinkItem(String itemId) {
        return plaidItemRepository.findByItemId(itemId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Linked item not found: " + itemId)))
                .flatMap(item -> Mono.fromRunnable(() -> doRemoveItem(item))
                        .subscribeOn(Schedulers.boundedElastic())
                        .then(plaidItemRepository.delete(item))
                        .thenReturn(true));
    }

    private String doCreateLinkToken() {
        LinkTokenCreateRequestUser user = new LinkTokenCreateRequestUser()
                .clientUserId("toyotafsbank-user-" + Instant.now().toEpochMilli());

        List<Products> products = plaidProperties.products().stream()
                .map(Products::fromValue)
                .toList();
        List<CountryCode> countryCodes = plaidProperties.countryCodes().stream()
                .map(CountryCode::fromValue)
                .toList();

        LinkTokenCreateRequest request = new LinkTokenCreateRequest()
                .user(user)
                .clientName("ToyotaFSBank")
                .products(products)
                .countryCodes(countryCodes)
                .language("en");

        if (plaidProperties.redirectUri() != null && !plaidProperties.redirectUri().isBlank()) {
            request.redirectUri(plaidProperties.redirectUri());
        }

        return execute(plaidApi.linkTokenCreate(request)).getLinkToken();
    }

    private ItemPublicTokenExchangeResponse doExchangePublicToken(String publicToken) {
        ItemPublicTokenExchangeRequest request = new ItemPublicTokenExchangeRequest()
                .publicToken(publicToken);
        return execute(plaidApi.itemPublicTokenExchange(request));
    }

    private List<PlaidAccountPayload> doGetAccounts(PlaidItem item) {
        AccountsBalanceGetRequest request = new AccountsBalanceGetRequest()
                .accessToken(item.accessToken());
        AccountsGetResponse response = execute(plaidApi.accountsBalanceGet(request));

        List<PlaidAccountPayload> accounts = new ArrayList<>();
        for (AccountBase account : response.getAccounts()) {
            accounts.add(PlaidAccountPayload.from(item.itemId(), item.institutionName(), account));
        }
        return accounts;
    }

    private List<PlaidTransactionPayload> doGetTransactions(PlaidItem item) {
        List<Transaction> added = new ArrayList<>();
        String cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            TransactionsSyncRequest request = new TransactionsSyncRequest()
                    .accessToken(item.accessToken())
                    .cursor(cursor);
            TransactionsSyncResponse response = execute(plaidApi.transactionsSync(request));

            added.addAll(response.getAdded());
            cursor = response.getNextCursor();
            hasMore = Boolean.TRUE.equals(response.getHasMore());
        }

        return added.stream()
                .map(transaction -> PlaidTransactionPayload.from(item.itemId(), item.institutionName(), transaction))
                .toList();
    }

    private void doRemoveItem(PlaidItem item) {
        execute(plaidApi.itemRemove(new ItemRemoveRequest().accessToken(item.accessToken())));
    }

    private <T> T execute(Call<T> call) {
        try {
            Response<T> response = call.execute();
            if (!response.isSuccessful()) {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown Plaid error";
                throw new PlaidApiException("Plaid API error (" + response.code() + "): " + errorBody);
            }
            return response.body();
        } catch (IOException e) {
            throw new PlaidApiException("Failed to reach Plaid: " + e.getMessage(), e);
        }
    }
}
