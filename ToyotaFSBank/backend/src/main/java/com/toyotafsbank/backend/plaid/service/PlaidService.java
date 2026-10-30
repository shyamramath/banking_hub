package com.toyotafsbank.backend.plaid.service;

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
import com.toyotafsbank.backend.exception.ResourceNotFoundException;
import com.toyotafsbank.backend.plaid.config.PlaidProperties;
import com.toyotafsbank.backend.plaid.dto.ExchangePublicTokenRequest;
import com.toyotafsbank.backend.plaid.dto.PlaidAccountResponse;
import com.toyotafsbank.backend.plaid.dto.PlaidTransactionResponse;
import com.toyotafsbank.backend.plaid.exception.PlaidApiException;
import com.toyotafsbank.backend.plaid.model.PlaidItem;
import com.toyotafsbank.backend.plaid.repository.PlaidItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlaidService {

    private final PlaidApi plaidApi;
    private final PlaidProperties plaidProperties;
    private final PlaidItemRepository plaidItemRepository;

    public String createLinkToken() {
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

        LinkTokenCreateResponse response = execute(plaidApi.linkTokenCreate(request));
        return response.getLinkToken();
    }

    public PlaidItem exchangePublicToken(ExchangePublicTokenRequest request) {
        ItemPublicTokenExchangeRequest exchangeRequest = new ItemPublicTokenExchangeRequest()
                .publicToken(request.publicToken());

        ItemPublicTokenExchangeResponse response = execute(plaidApi.itemPublicTokenExchange(exchangeRequest));

        PlaidItem item = PlaidItem.builder()
                .itemId(response.getItemId())
                .accessToken(response.getAccessToken())
                .institutionId(request.institutionId())
                .institutionName(request.institutionName())
                .linkedAt(Instant.now())
                .build();

        return plaidItemRepository.save(item);
    }

    public List<PlaidItem> listLinkedItems() {
        return plaidItemRepository.findAll();
    }

    public List<PlaidAccountResponse> getAllAccounts() {
        List<PlaidAccountResponse> accounts = new ArrayList<>();
        for (PlaidItem item : plaidItemRepository.findAll()) {
            AccountsBalanceGetRequest request = new AccountsBalanceGetRequest()
                    .accessToken(item.getAccessToken());
            AccountsGetResponse response = execute(plaidApi.accountsBalanceGet(request));
            for (AccountBase account : response.getAccounts()) {
                accounts.add(PlaidAccountResponse.from(item.getItemId(), item.getInstitutionName(), account));
            }
        }
        return accounts;
    }

    public List<PlaidTransactionResponse> getAllTransactions() {
        List<PlaidTransactionResponse> transactions = new ArrayList<>();
        for (PlaidItem item : plaidItemRepository.findAll()) {
            for (Transaction transaction : syncTransactions(item.getAccessToken())) {
                transactions.add(PlaidTransactionResponse.from(item.getItemId(), item.getInstitutionName(), transaction));
            }
        }
        transactions.sort((a, b) -> b.date().compareTo(a.date()));
        return transactions;
    }

    public void unlinkItem(String itemId) {
        PlaidItem item = plaidItemRepository.findByItemId(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Linked item not found: " + itemId));

        execute(plaidApi.itemRemove(new ItemRemoveRequest().accessToken(item.getAccessToken())));
        plaidItemRepository.delete(item);
    }

    private List<Transaction> syncTransactions(String accessToken) {
        List<Transaction> added = new ArrayList<>();
        String cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            TransactionsSyncRequest request = new TransactionsSyncRequest()
                    .accessToken(accessToken)
                    .cursor(cursor);
            TransactionsSyncResponse response = execute(plaidApi.transactionsSync(request));

            added.addAll(response.getAdded());
            cursor = response.getNextCursor();
            hasMore = Boolean.TRUE.equals(response.getHasMore());
        }

        return added;
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
