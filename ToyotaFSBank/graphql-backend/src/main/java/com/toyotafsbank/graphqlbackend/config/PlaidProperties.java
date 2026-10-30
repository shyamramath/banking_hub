package com.toyotafsbank.graphqlbackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "plaid")
public record PlaidProperties(
        String clientId,
        String secret,
        String env,
        List<String> products,
        List<String> countryCodes,
        String redirectUri
) {
}
