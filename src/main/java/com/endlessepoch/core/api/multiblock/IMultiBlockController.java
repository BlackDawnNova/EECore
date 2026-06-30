package com.endlessepoch.core.api.multiblock;

import java.util.UUID;

/**
 * Interface for block entities that act as multiblock controllers.
 * <p>
 * When a multiblock forms, the controller receives:
 * - The owner's UUID and name (stamped into the controller)
 * - Callbacks for formation and break
 * <p>
 * 多方块结构控制器的方块实体接口。
 * <p>
 * 当多方块结构形成时，控制器将接收：
 * - 拥有者的 UUID 和名称（写入控制器）
 * - 形成和破坏的回调
 */
public interface IMultiBlockController {

    /** Unique ID for this controller node. / 此控制器节点的唯一 ID。 */
    UUID getNodeId();

    /** Whether the multiblock is currently formed and valid. / 多方块结构当前是否已形成且有效。 */
    boolean isFormed();

    /** Called when the multiblock is successfully formed. / 当多方块结构成功形成时调用。 */
    void onMultiblockFormed();

    /** Called when the multiblock breaks (any reason). / 当多方块结构因任何原因破坏时调用。 */
    void onMultiblockBroken();

    /** The player who owns this structure (stamped at formation). / 拥有此结构的玩家（在形成时写入）。 */
    UUID getOwnerUUID();

    /** Display name of the owner. / 拥有者的显示名称。 */
    String getOwnerName();

    /**
     * Stamp the owner on first formation, storing UUID and name in NBT.
     * <p>
     * 在首次形成时写入拥有者信息，将 UUID 和名称存储到 NBT 中。
     */
    void stampOwner(UUID owner, String name);
}
