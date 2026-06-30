package com.endlessepoch.core.api.team;

import java.util.Optional;
import java.util.UUID;

/**
 * Team identity provider — pluggable interface for energy network ownership.
 * <p>
 * If a player belongs to a team, their generators/consumers share the network.
 * EECore provides a default implementation; FTB Teams is auto-bridged when present.
 * <p>
 * 队伍身份提供者 —— 能量网络所有权的可插拔接口。
 * <p>
 * 如果玩家属于一个队伍，他们的发电机/用电器将共享同一个网络。
 * EECore 提供默认实现；FTB Teams 会在存在时自动桥接。
 */
public interface ITeamProvider {

    /**
     * Get the team a player belongs to. Empty if the player is solo.
     * <p>
     * 获取玩家所属的队伍。如果玩家是单独一人则返回空。
     */
    Optional<TeamData> getTeam(UUID playerId);

    /**
     * Get team by its ID.
     * <p>
     * 根据队伍 ID 获取队伍。
     */
    Optional<TeamData> getTeamById(UUID teamId);

    /**
     * Create a new team. Returns the created team data.
     * <p>
     * 创建新队伍。返回创建的队伍数据。
     */
    TeamData createTeam(UUID ownerId, String name);

    /**
     * Disband (delete) a team. Only the owner can do this.
     * <p>
     * 解散队伍。只有所有者可以执行此操作。
     */
    boolean disbandTeam(UUID teamId, UUID requesterId);

    /**
     * Invite a player to the team.
     * <p>
     * 邀请玩家加入队伍。
     */
    boolean invitePlayer(UUID teamId, UUID inviterId, UUID targetId);

    /**
     * Kick a player from the team.
     * <p>
     * 将玩家踢出队伍。
     */
    boolean kickPlayer(UUID teamId, UUID kickerId, UUID targetId);

    /**
     * Transfer ownership to another member.
     * <p>
     * 将所有权转让给另一名成员。
     */
    boolean transferOwnership(UUID teamId, UUID currentOwnerId, UUID newOwnerId);

    /**
     * Set ally status between two teams.
     * <p>
     * 设置两个队伍之间的盟友状态。
     */
    boolean setAlly(UUID teamIdA, UUID teamIdB, boolean ally);

    /**
     * Check if two players share a network (same team or allies).
     * <p>
     * 检查两名玩家是否共享同一网络（相同队伍或盟友）。
     */
    boolean shareNetwork(UUID playerA, UUID playerB);

    /**
     * Name of this provider for diagnostics.
     * <p>
     * 此提供者的名称，用于诊断。
     */
    String getProviderName();
}
