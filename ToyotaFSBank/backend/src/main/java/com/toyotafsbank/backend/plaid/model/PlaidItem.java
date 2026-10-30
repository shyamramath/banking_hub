package com.toyotafsbank.backend.plaid.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "plaid_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaidItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String itemId;

    @Column(nullable = false)
    private String accessToken;

    private String institutionId;

    private String institutionName;

    private Instant linkedAt;
}
