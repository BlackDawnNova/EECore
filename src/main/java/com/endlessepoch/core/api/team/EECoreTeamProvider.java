package com.endlessepoch.core.api.team;

import java.util.*;

/**
 * EECore's built-in team provider.
 * <p>
 * Simple in-memory team system. Teams persist to world data or player NBT
 * depending on integration needs. Used as fallback when FTB Teams is absent.
 * <p>
 * EECore 内置的队伍提供器。
 * <p>
 * 简单的内存队伍系统。队伍根据集成需求保存到世界数据或玩家 NBT 中。
 * 当 FTB Teams 不存在时作为回退方案使用。
 */
public class EECoreTeamProvider implements ITeamProvider {

    private final Map<UUID, TeamData> teams = new HashMap<>();
    private final Map<UUID, UUID> playerTeamMap = new HashMap<>();

    public EECoreTeamProvider() {}

    /**
     * Get the team for a given player.
     * <p>
     * 获取指定玩家的队伍。
     */
    @Override
    public Optional<TeamData> getTeam(UUID playerId) {
        UUID teamId = playerTeamMap.get(playerId);
        if (teamId == null) return Optional.empty();
        return Optional.ofNullable(teams.get(teamId));
    }

    /**
     * Get a team by its unique ID.
     * <p>
     * 通过唯一 ID 获取队伍。
     */
    @Override
    public Optional<TeamData> getTeamById(UUID teamId) {
        return Optional.ofNullable(teams.get(teamId));
    }

    /**
     * Create a new team with the given owner and name.
     * <p>
     * 使用指定的拥有者和名称创建新队伍。
     */
    @Override
    public TeamData createTeam(UUID ownerId, String name) {
        UUID teamId = UUID.randomUUID();
        TeamData team = TeamData.create(teamId, name, ownerId);
        teams.put(teamId, team);
        playerTeamMap.put(ownerId, teamId);
        return team;
    }

    /**
     * Disband a team. Only the owner may disband.
     * <p>
     * 解散队伍。仅拥有者可以解散。
     */
    @Override
    public boolean disbandTeam(UUID teamId, UUID requesterId) {
        TeamData team = teams.get(teamId);
        if (team == null || !team.owner().equals(requesterId)) return false;
        for (UUID member : team.members()) {
            playerTeamMap.remove(member);
        }
        teams.remove(teamId);
        return true;
    }

    /**
     * Invite a player to join the team. Only the owner may invite.
     * <p>
     * 邀请玩家加入队伍。仅拥有者可以邀请。
     */
    @Override
    public boolean invitePlayer(UUID teamId, UUID inviterId, UUID targetId) {
        TeamData team = teams.get(teamId);
        if (team == null || !team.owner().equals(inviterId)) return false;
        if (playerTeamMap.containsKey(targetId)) return false;
        TeamData updated = team.withMember(targetId);
        teams.put(teamId, updated);
        playerTeamMap.put(targetId, teamId);
        return true;
    }

    /**
     * Kick a player from the team. Only the owner may kick, and cannot kick themselves.
     * <p>
     * 将玩家踢出队伍。仅拥有者可以踢人，且不能踢自己。
     */
    @Override
    public boolean kickPlayer(UUID teamId, UUID kickerId, UUID targetId) {
        TeamData team = teams.get(teamId);
        if (team == null || !team.owner().equals(kickerId)) return false;
        if (targetId.equals(team.owner())) return false;
        TeamData updated = team.withoutMember(targetId);
        teams.put(teamId, updated);
        playerTeamMap.remove(targetId);
        return true;
    }

    /**
     * Transfer team ownership to another member.
     * <p>
     * 将队伍所有权转移给另一名成员。
     */
    @Override
    public boolean transferOwnership(UUID teamId, UUID currentOwnerId, UUID newOwnerId) {
        TeamData team = teams.get(teamId);
        if (team == null || !team.owner().equals(currentOwnerId)) return false;
        if (!team.members().contains(newOwnerId)) return false;
        Set<UUID> members = new HashSet<>(team.members());
        teams.put(teamId, new TeamData(teamId, team.name(), newOwnerId, members, team.allies()));
        return true;
    }

    /**
     * Set or remove an ally relationship between two teams.
     * <p>
     * 设置或移除两个队伍之间的盟友关系。
     */
    @Override
    public boolean setAlly(UUID teamIdA, UUID teamIdB, boolean ally) {
        TeamData teamA = teams.get(teamIdA);
        if (teamA == null) return false;
        if (ally) {
            teams.put(teamIdA, teamA.withAlly(teamIdB));
        } else {
            Set<UUID> newAllies = new HashSet<>(teamA.allies());
            newAllies.remove(teamIdB);
            teams.put(teamIdA, new TeamData(teamA.teamId(), teamA.name(), teamA.owner(),
                    teamA.members(), newAllies));
        }
        return true;
    }

    /**
     * Check whether two players share a network (same team or allied teams).
     * <p>
     * 检查两个玩家是否共享网络（同一队伍或盟友队伍）。
     */
    @Override
    public boolean shareNetwork(UUID playerA, UUID playerB) {
        Optional<TeamData> teamA = getTeam(playerA);
        Optional<TeamData> teamB = getTeam(playerB);
        if (teamA.isEmpty() || teamB.isEmpty()) return false;
        if (teamA.get().teamId().equals(teamB.get().teamId())) return true;
        return teamA.get().allies().contains(teamB.get().teamId());
    }

    /**
     * Get the name of this provider for diagnostic purposes.
     * <p>
     * 获取此提供器的名称，用于诊断。
     */
    @Override
    public String getProviderName() { return "EECore"; }
}
