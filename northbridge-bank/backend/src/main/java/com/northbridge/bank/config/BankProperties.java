package com.northbridge.bank.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * All tunables in one place (application.yml: {@code bank.*}).
 */
@ConfigurationProperties(prefix = "bank")
public record BankProperties(Security security, Limits limits) {

    public record Security(
            /* Expected "iss" claim - the PUBLIC issuer URL the browser logged in against. */
            String issuer,
            /* Where to fetch signing keys - may be an internal URL (e.g. http://keycloak:8080/...). */
            String jwkSetUri,
            /* Expected "aud" claim - tokens minted for other APIs are rejected. */
            @DefaultValue("bank-backend") String audience) {
    }

    public record Limits(
            @DefaultValue("10") int batchMaxSize,
            @DefaultValue("10000.00") BigDecimal maxTransferAmount,
            @DefaultValue("65536") long maxBodyBytes) {
    }
}
