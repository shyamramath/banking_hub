package com.example.ensenta.client;

import java.time.Instant;

/**
 * Fields Ensenta expects on every request: who is depositing, from which device,
 * and (after StartSession) the session credentials.
 */
public record DepositorContext(
        String depositorIpAddress,
        String deviceIdentifier,
        String timeZone,            // e.g. "CST -06:00"
        String sessionToken,        // null before StartSession
        String sessionStateId) {    // null before StartSession

    public String localDateTime() {
        return Instant.now().toString();   // ISO-8601, as in Jack Henry's examples
    }

    public DepositorContext withSession(String token, String stateId) {
        return new DepositorContext(depositorIpAddress, deviceIdentifier, timeZone, token, stateId);
    }
}
