package com.northbridge.bank.security;

import com.northbridge.bank.config.BankProperties;
import java.util.Collection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * URL-level security is deliberately coarse: there is exactly one business endpoint, so all
 * we can say here is "must carry a valid token". Fine-grained authorization happens per
 * operation inside {@link com.northbridge.bank.dispatch.OperationDispatcher}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
            // Stateless bearer-token API. CSRF is enforced at the BFF, where the cookie lives.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/execute", "/api/execute/batch").authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtConverter())))
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .frameOptions(f -> f.deny()));
        return http.build();
    }

    /**
     * Validates signature (RS256 only), exp/nbf, issuer and audience.
     * Keys are fetched lazily from the JWKS endpoint and cached by Nimbus.
     */
    @Bean
    JwtDecoder jwtDecoder(BankProperties props) {
        BankProperties.Security sec = props.security();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(sec.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> issuerAndTime = JwtValidators.createDefaultWithIssuer(sec.issuer());
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<Collection<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(sec.audience()));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerAndTime, audience));
        return decoder;
    }
}
