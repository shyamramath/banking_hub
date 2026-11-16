package com.northbridge.bank;

import com.northbridge.bank.config.BankProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BankProperties.class)
public class BankBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankBackendApplication.class, args);
    }
}
