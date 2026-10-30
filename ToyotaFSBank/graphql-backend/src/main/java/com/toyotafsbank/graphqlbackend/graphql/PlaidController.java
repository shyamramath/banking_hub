package com.toyotafsbank.graphqlbackend.graphql;

import com.toyotafsbank.graphqlbackend.graphql.dto.ExchangePublicTokenInput;
import com.toyotafsbank.graphqlbackend.graphql.dto.LinkedItemPayload;
import com.toyotafsbank.graphqlbackend.graphql.dto.PlaidAccountPayload;
import com.toyotafsbank.graphqlbackend.graphql.dto.PlaidTransactionPayload;
import com.toyotafsbank.graphqlbackend.service.PlaidService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
@RequiredArgsConstructor
public class PlaidController {

    private final PlaidService plaidService;

    @MutationMapping
    public Mono<String> createPlaidLinkToken() {
        return plaidService.createLinkToken();
    }

    @MutationMapping
    public Mono<LinkedItemPayload> exchangePlaidPublicToken(@Argument ExchangePublicTokenInput input) {
        return plaidService.exchangePublicToken(input.publicToken(), input.institutionId(), input.institutionName())
                .map(LinkedItemPayload::from);
    }

    @MutationMapping
    public Mono<Boolean> unlinkPlaidItem(@Argument String itemId) {
        return plaidService.unlinkItem(itemId);
    }

    @QueryMapping
    public Flux<LinkedItemPayload> plaidItems() {
        return plaidService.listLinkedItems().map(LinkedItemPayload::from);
    }

    @QueryMapping
    public Flux<PlaidAccountPayload> plaidAccounts() {
        return plaidService.getAllAccounts();
    }

    @QueryMapping
    public Flux<PlaidTransactionPayload> plaidTransactions() {
        return plaidService.getAllTransactions();
    }
}
