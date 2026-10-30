package com.toyotafsbank.graphqlbackend.graphql;

import com.toyotafsbank.graphqlbackend.graphql.dto.AccountInput;
import com.toyotafsbank.graphqlbackend.graphql.dto.AccountPayload;
import com.toyotafsbank.graphqlbackend.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @QueryMapping
    public Flux<AccountPayload> accounts() {
        return accountService.findAll().map(AccountPayload::from);
    }

    @QueryMapping
    public Mono<AccountPayload> account(@Argument Long id) {
        return accountService.findById(id).map(AccountPayload::from);
    }

    @MutationMapping
    public Mono<AccountPayload> createAccount(@Argument AccountInput input) {
        return accountService.create(input.ownerName(), input.accountType(), input.balance())
                .map(AccountPayload::from);
    }

    @MutationMapping
    public Mono<AccountPayload> updateAccount(@Argument Long id, @Argument AccountInput input) {
        return accountService.update(id, input.ownerName(), input.accountType(), input.balance())
                .map(AccountPayload::from);
    }

    @MutationMapping
    public Mono<Boolean> deleteAccount(@Argument Long id) {
        return accountService.delete(id);
    }
}
