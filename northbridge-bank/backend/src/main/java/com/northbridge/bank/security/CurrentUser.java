package com.northbridge.bank.security;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The caller, derived ONLY from the validated JWT. Handlers must use {@link #id()} for
 * ownership checks and never trust a user/customer id sent in the payload.
 */
public record CurrentUser(String id, String username, String displayName, Set<String> authorities) {

    public static CurrentUser from(Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException("Expected a JWT-authenticated request");
        }
        Jwt jwt = token.getToken();
        String username = firstNonBlank(jwt.getClaimAsString("preferred_username"), jwt.getSubject());
        String name = firstNonBlank(jwt.getClaimAsString("name"), username);
        Set<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
        return new CurrentUser(jwt.getSubject(), username, name, authorities);
    }

    public boolean hasAuthority(String authority) {
        return authorities.contains(authority);
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
