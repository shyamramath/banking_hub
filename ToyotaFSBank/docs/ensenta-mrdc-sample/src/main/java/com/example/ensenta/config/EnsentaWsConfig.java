package com.example.ensenta.config;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.util.StringUtils;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.transport.http.HttpsUrlConnectionMessageSender;

/**
 * Two SOAP clients, one per Ensenta service:
 *   - PartnerSSORequest3     : StartSession / GetSessionInfo / EndSession
 *   - PartnerDepositRequest3 : CreateDepositBatch / AddDepositItem / ... / PostDepositBatch
 *
 * Authentication is mutual TLS: Ensenta validates the client x509 certificate
 * against the PartnerId and environment, then issues SessionToken + SessionStateId.
 */
@Configuration
@EnableRetry
@EnableConfigurationProperties(EnsentaProperties.class)
public class EnsentaWsConfig {

    @Bean
    Jaxb2Marshaller ssoMarshaller() {
        Jaxb2Marshaller m = new Jaxb2Marshaller();
        m.setPackagesToScan("com.example.ensenta.gen.sso");
        return m;
    }

    @Bean
    Jaxb2Marshaller depositMarshaller() {
        Jaxb2Marshaller m = new Jaxb2Marshaller();
        m.setPackagesToScan("com.example.ensenta.gen.deposit");
        // Check images are large Base64 strings; MTOM is not described in the docs, so it stays off.
        return m;
    }

    @Bean
    HttpsUrlConnectionMessageSender mutualTlsSender(EnsentaProperties props) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(props.clientCert().keystoreType());
        try (InputStream in = props.clientCert().keystore().getInputStream()) {
            keyStore.load(in, props.clientCert().keystorePassword().toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, props.clientCert().keystorePassword().toCharArray());

        HttpsUrlConnectionMessageSender sender = new HttpsUrlConnectionMessageSender();
        sender.setKeyManagers(kmf.getKeyManagers());

        if (StringUtils.hasText(props.truststore().path())) {
            KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
            try (InputStream in = new FileInputStream(props.truststore().path())) {
                trust.load(in, props.truststore().password().toCharArray());
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trust);
            sender.setTrustManagers(tmf.getTrustManagers());
        }
        sender.setConnectionTimeout(java.time.Duration.ofMillis(props.timeouts().connectMs()));
        sender.setReadTimeout(java.time.Duration.ofMillis(props.timeouts().readMs()));
        return sender;
    }

    @Bean
    WebServiceTemplate ssoTemplate(Jaxb2Marshaller ssoMarshaller,
                                   HttpsUrlConnectionMessageSender mutualTlsSender,
                                   EnsentaProperties props) {
        WebServiceTemplate t = new WebServiceTemplate(ssoMarshaller);
        t.setDefaultUri(props.ssoEndpoint());
        t.setMessageSender(mutualTlsSender);
        return t;
    }

    @Bean
    WebServiceTemplate depositTemplate(Jaxb2Marshaller depositMarshaller,
                                       HttpsUrlConnectionMessageSender mutualTlsSender,
                                       EnsentaProperties props) {
        WebServiceTemplate t = new WebServiceTemplate(depositMarshaller);
        t.setDefaultUri(props.depositEndpoint());
        t.setMessageSender(mutualTlsSender);
        return t;
    }
}
