package com.endlessepoch.core.api.routing;

import java.util.*;

/**
 * Energy router — path discovery and load balancing for NovaNet.
 * <p>
 * Phase 3 full implementation. Default: direct connection only.
 */
public interface IEnergyRouter {

    /** Find all paths from one node to another. */
    List<UUID> findPaths(UUID fromId, UUID toId);

    /** Find the optimal (lowest loss / highest bandwidth) path. */
    Optional<UUID> findOptimalPath(UUID fromId, UUID toId);

    /** Update load on a given path. */
    void updatePathLoad(UUID pathId, double load);

    /** Get the provider name for diagnostics. */
    String getProviderName();
}
