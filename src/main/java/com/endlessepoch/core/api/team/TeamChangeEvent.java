package com.endlessepoch.core.api.team;

import net.neoforged.bus.api.Event;

import java.util.UUID;

/**
 * Fired on NeoForge EVENT_BUS when a team changes.
 * Other mods listen for energy rebalancing, permission updates, etc.
 * <p>
 * 当队伍发生变化时在 NeoForge EVENT_BUS 上触发。
 * 其他模组监听此事件以进行能量重新平衡、权限更新等。
 */
public class TeamChangeEvent extends Event {

    public enum ChangeType {
        CREATED,
        DISBANDED,
        MEMBER_JOINED,
        MEMBER_LEFT,
        OWNERSHIP_TRANSFERRED,
        ALLY_ADDED,
        ALLY_REMOVED
    }

    private final UUID teamId;
    private final ChangeType type;
    private final UUID affectedPlayer;

    public TeamChangeEvent(UUID teamId, ChangeType type, UUID affectedPlayer) {
        this.teamId = teamId;
        this.type = type;
        this.affectedPlayer = affectedPlayer;
    }

    public UUID getTeamId() { return teamId; }
    public ChangeType getChangeType() { return type; }
    public UUID getAffectedPlayer() { return affectedPlayer; }
}
