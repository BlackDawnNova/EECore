package com.endlessepoch.core.api.multiblock;

import java.util.UUID;

/**
 * Interface for block entities that act as multiblock controllers.
 * <p>
 * When a multiblock forms, the controller receives:
 * - The owner's UUID and name (stamped into the controller)
 * - Callbacks for formation and break
 */
public interface IMultiBlockController {

    /** Unique ID for this controller node. */
    UUID getNodeId();

    /** Whether the multiblock is currently formed and valid. */
    boolean isFormed();

    /** Called when the multiblock is successfully formed. */
    void onMultiblockFormed();

    /** Called when the multiblock breaks (any reason). */
    void onMultiblockBroken();

    /** The player who owns this structure (stamped at formation). */
    UUID getOwnerUUID();

    /** Display name of the owner. */
    String getOwnerName();

    /**
     * Stamp the owner on first formation, storing UUID and name in NBT.
     */
    void stampOwner(UUID owner, String name);
}
