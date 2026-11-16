package com.northbridge.bank;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.northbridge.bank.domain.BankStore;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Builds the same authorities {@code KeycloakJwtConverter} would produce from a real token. */
final class TestTokens {

    static final String ALL_CUSTOMER_SCOPES = "accounts:read transfers:write";

    private TestTokens() {
    }

    static RequestPostProcessor alice(String scopes) {
        return user(BankStore.ALICE, "alice", "Alice Carter", scopes, "customer");
    }

    static RequestPostProcessor bob(String scopes) {
        return user(BankStore.BOB, "bob", "Bob Nguyen", scopes, "customer");
    }

    static RequestPostProcessor carol(String scopes) {
        return user(BankStore.CAROL, "carol", "Carol Diaz", scopes, "auditor");
    }

    static RequestPostProcessor user(String sub, String username, String name, String scopes, String... roles) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String s : scopes.split(" ")) {
            if (!s.isBlank()) authorities.add(new SimpleGrantedAuthority("SCOPE_" + s));
        }
        for (String r : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + r));
        }
        return jwt()
                .jwt(j -> j.subject(sub).claim("preferred_username", username).claim("name", name))
                .authorities(authorities);
    }
}
