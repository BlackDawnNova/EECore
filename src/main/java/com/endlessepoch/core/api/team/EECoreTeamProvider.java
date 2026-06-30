package com.endlessepoch.core.api.team;

import java.util.*;

/**
 * EECore's built-in team provider.
 * <p>
 * Simple in-memory team system. Teams persist to world data or player NBT
 * depending on integration needs. Used as fallback when FTB Teams is absent.
 */
public class EECoreTeamProvider implements ITeamProvider {

    private final Map<UUID, TeamData> teams = new HashMap<>();
    private final Map<UUID, UUID> playerTeamMap = new HashMap<>(); // playerId → teamId

    public EECoreTeamProvider() {}

    @Override
    public Optional<TeamData> getTeam(UUID playerId) {
        UUID teamId = playerTeamMap.get(playerId);
        if (teamId == null) return Optional.empty();
        return Optional.ofNullable(teams.get(teamId));
    }

    @Override
    public Optional<TeamData> getTeamById(UUID teamId) {
        return Optional.ofNullable(teams.get(teamId));
    }

    @Override
    public TeamData createTeam(UUID ownerId, String name) {
        UUID teamId = UUID.randomUUID();
        TeamData team = TeamData.create(teamId, name, ownerId);
        teams.put(teamId, team);
        playerTeamMap.put(ownerId, teamId);
        return team;
    }

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

    @Override
    public boolean invitePlayer(UUID teamId, UUID inviterId, UUID targetId) {
        TeamData team = teams.get(teamId);
        if (team == null || !team.owner().equals(inviterId)) return false;
        if (playerTeamMap.containsKey(targetId)) return false; // already in a team
        TeamData updated = team.withMember(targetId);
        teams.put(teamId, updated);
        playerTeamMap.put(targetId, teamId);
        return true;
    }

    @Override
    public boolean kickPlayer(UUID teamId, UUID kickerId, UUID targetId) {
        TeamData team = teams.get(teamId);
        if (team == null || !team.owner().equals(kickerId)) return false;
        if (targetId.equals(team.owner())) return false; // can't kick owner
        TeamData updated = team.withoutMember(targetId);
        teams.put(teamId, updated);
        playerTeamMap.remove(targetId);
        return true;
    }

    @Override
    public boolean transferOwnership(UUID teamId, UUID currentOwnerId, UUID newOwnerId) {
        TeamData team = teams.get(teamId);
        if (team == null || !team.owner().equals(currentOwnerId)) return false;
        if (!team.members().contains(newOwnerId)) return false;
        Set<UUID> members = new HashSet<>(team.members());
        teams.put(teamId, new TeamData(teamId, team.name(), newOwnerId, members, team.allies()));
        return true;
    }

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

    @Override
    public boolean shareNetwork(UUID playerA, UUID playerB) {
        Optional<TeamData> teamA = getTeam(playerA);
        Optional<TeamData> teamB = getTeam(playerB);
        if (teamA.isEmpty() || teamB.isEmpty()) return false;
        if (teamA.get().teamId().equals(teamB.get().teamId())) return true;
        return teamA.get().allies().contains(teamB.get().teamId());
    }

    @Override
    public String getProviderName() { return "EECore"; }
}
