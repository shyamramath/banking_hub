package com.toyotafsbank.graphqlbackend.config;

import com.plaid.client.ApiClient;
import com.plaid.client.request.PlaidApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(PlaidProperties.class)
public class PlaidClientConfig {

    @Bean
    public PlaidApi plaidApi(PlaidProperties properties) {
        Map<String, String> apiKeys = new HashMap<>();
        apiKeys.put("clientId", properties.clientId());
        apiKeys.put("secret", properties.secret());
        apiKeys.put("plaidVersion", "2020-09-14");

        ApiClient apiClient = new ApiClient(apiKeys);
        apiClient.setPlaidAdapter("production".equalsIgnoreCase(properties.env())
                ? ApiClient.Production
                : ApiClient.Sandbox);

        return apiClient.createService(PlaidApi.class);
    }
}
