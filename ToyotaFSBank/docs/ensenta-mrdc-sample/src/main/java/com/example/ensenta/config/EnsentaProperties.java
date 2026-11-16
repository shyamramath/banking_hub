package com.example.ensenta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "ensenta")
public record EnsentaProperties(
        String partnerId,
        int languageId,
        String ssoEndpoint,
        String depositEndpoint,
        String soapActionBaseSso,
        String soapActionBaseDeposit,
        ClientCert clientCert,
        Truststore truststore,
        Timeouts timeouts,
        Poll batchSummaryPoll) {

    public record ClientCert(Resource keystore, String keystorePassword, String keystoreType) {}
    public record Truststore(String path, String password) {}
    public record Timeouts(int connectMs, int readMs) {}
    public record Poll(int maxAttempts, long initialDelayMs, long maxDelayMs) {}
}
