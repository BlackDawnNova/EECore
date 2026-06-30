package com.endlessepoch.core.api.team;

import java.util.*;

/**
 * Immutable team data snapshot.
 * <p>
 * {@code owner} is the team creator with full control (kick, disband, transfer).
 * {@code allies} are trusted teams that can share energy networks.
 */
public record TeamData(
        UUID teamId,
        String name,
        UUID owner,
        Set<UUID> members,
        Set<UUID> allies
) {
    public TeamData {
        members = Set.copyOf(members);
        allies = Set.copyOf(allies);
    }

    public static TeamData create(UUID teamId, String name, UUID owner) {
        Set<UUID> members = new HashSet<>();
        members.add(owner);
        return new TeamData(teamId, name, owner, members, Set.of());
    }

    public TeamData withMember(UUID player) {
        Set<UUID> newMembers = new HashSet<>(members);
        newMembers.add(player);
        return new TeamData(teamId, name, owner, newMembers, allies);
    }

    public TeamData withoutMember(UUID player) {
        Set<UUID> newMembers = new HashSet<>(members);
        newMembers.remove(player);
        return new TeamData(teamId, name, owner, newMembers, allies);
    }

    public TeamData withAlly(UUID team) {
        Set<UUID> newAllies = new HashSet<>(allies);
        newAllies.add(team);
        return new TeamData(teamId, name, owner, members, newAllies);
    }
}
