package com.northbridge.bank.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Maps a Keycloak access token to Spring authorities:
 * <ul>
 *   <li>{@code scope: "accounts:read transfers:write"} &rarr; {@code SCOPE_accounts:read}, ...
 *       (what the <em>client</em> was allowed to ask for)</li>
 *   <li>{@code realm_access.roles: ["customer"]} &rarr; {@code ROLE_customer}
 *       (what the <em>user</em> is)</li>
 * </ul>
 * The principal name is the stable {@code sub} claim, never a client-supplied value.
 */
public class KeycloakJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>(scopes.convert(jwt));

        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
            for (Object role : roles) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
