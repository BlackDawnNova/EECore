package com.endlessepoch.core.api.credit;

import java.util.UUID;

/**
 * Energy credit system — fair distribution, anti-vampire, overdraft limits.
 * <p>
 * Phase 3 full implementation. Default: permissive (unlimited).
 * Each team earns credit by producing energy, spends by consuming.
 */
public interface IEnergyCreditSystem {

    /** Get the credit balance for a node. */
    long getBalance(UUID nodeId);

    /** Add credit (earned through energy production). */
    void addCredit(UUID nodeId, long amount);

    /** Subtract credit (spent through energy consumption). */
    boolean subtractCredit(UUID nodeId, long amount);

    /** Check if a node has enough credit to withdraw a given amount. */
    boolean hasSufficientCredit(UUID nodeId, long amount);

    /** Get the provider name for diagnostics. */
    String getProviderName();
}
