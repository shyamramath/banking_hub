package com.northbridge.bank.domain;

import java.time.LocalDate;

public record CustomerProfile(String userId, String displayName, String segment, LocalDate memberSince,
                              String maskedEmail, String relationshipManager) {
}
