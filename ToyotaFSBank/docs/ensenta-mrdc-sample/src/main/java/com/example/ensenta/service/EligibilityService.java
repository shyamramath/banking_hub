package com.example.ensenta.service;

import com.example.ensenta.client.EnsentaClient.AccountHolder;

/**
 * Ensenta shifts enrollment to the mobile banking provider: we decide who may deposit
 * and which accounts they may deposit into, then pass only those in StartSession.
 *
 * Implement against your own systems, e.g. the core (jXchange / SilverLake / Symitar),
 * your digital banking user store, and your RDC enrollment and limits rules.
 */
public interface EligibilityService {

    /** @throws IllegalStateException if the user is not enrolled for mobile deposit */
    AccountHolder eligibleHolder(String userId);
}
