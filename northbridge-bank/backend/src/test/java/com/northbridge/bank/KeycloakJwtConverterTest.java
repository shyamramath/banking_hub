package com.northbridge.bank;

import static org.assertj.core.api.Assertions.assertThat;

import com.northbridge.bank.security.KeycloakJwtConverter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtConverterTest {

    @Test
    void mapsScopesAndRealmRoles() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("11111111-1111-4111-8111-111111111111")
                .claim("scope", "openid accounts:read transfers:write")
                .claim("realm_access", Map.of("roles", List.of("customer", "offline_access")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        var auth = new KeycloakJwtConverter().convert(jwt);

        assertThat(auth.getName()).isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("SCOPE_accounts:read", "SCOPE_transfers:write", "ROLE_customer")
                .doesNotContain("ROLE_auditor");
    }
}
