package com.toyotafsbank.backend.plaid.controller;

import com.toyotafsbank.backend.plaid.dto.ExchangePublicTokenRequest;
import com.toyotafsbank.backend.plaid.dto.LinkTokenResponse;
import com.toyotafsbank.backend.plaid.dto.LinkedItemResponse;
import com.toyotafsbank.backend.plaid.dto.PlaidAccountResponse;
import com.toyotafsbank.backend.plaid.dto.PlaidTransactionResponse;
import com.toyotafsbank.backend.plaid.service.PlaidService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/plaid")
@RequiredArgsConstructor
public class PlaidController {

    private final PlaidService plaidService;

    @PostMapping("/link-token")
    public LinkTokenResponse createLinkToken() {
        return new LinkTokenResponse(plaidService.createLinkToken());
    }

    @PostMapping("/exchange-token")
    public ResponseEntity<LinkedItemResponse> exchangeToken(@Valid @RequestBody ExchangePublicTokenRequest request) {
        var item = plaidService.exchangePublicToken(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(LinkedItemResponse.from(item));
    }

    @GetMapping("/items")
    public List<LinkedItemResponse> getLinkedItems() {
        return plaidService.listLinkedItems().stream()
                .map(LinkedItemResponse::from)
                .toList();
    }

    @GetMapping("/accounts")
    public List<PlaidAccountResponse> getAccounts() {
        return plaidService.getAllAccounts();
    }

    @GetMapping("/transactions")
    public List<PlaidTransactionResponse> getTransactions() {
        return plaidService.getAllTransactions();
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> unlinkItem(@PathVariable String itemId) {
        plaidService.unlinkItem(itemId);
        return ResponseEntity.noContent().build();
    }
}
