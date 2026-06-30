package com.endlessepoch.core.api.security;

import java.util.UUID;

/**
 * Energy security provider — identity verification and rate limiting.
 * <p>
 * Phase 3 implementation. Default: permissive (allow all).
 * Other mods can implement to add authentication, DDOS protection, etc.
 */
public interface IEnergySecurityProvider {

    /** Check if a node is authorized to participate in the network. */
    boolean isAuthorized(UUID nodeId);

    /** Check if a node exceeds rate limits for receive/extract operations. */
    boolean isRateLimited(UUID nodeId);

    /** Return the provider name for diagnostics. */
    String getProviderName();
}
