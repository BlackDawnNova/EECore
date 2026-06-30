package com.endlessepoch.core.api.team;

import java.util.Optional;
import java.util.UUID;

/**
 * Team identity provider — pluggable interface for energy network ownership.
 * <p>
 * If a player belongs to a team, their generators/consumers share the network.
 * EECore provides a default implementation; FTB Teams is auto-bridged when present.
 */
public interface ITeamProvider {

    /** Get the team a player belongs to. Empty if the player is solo. */
    Optional<TeamData> getTeam(UUID playerId);

    /** Get team by its ID. */
    Optional<TeamData> getTeamById(UUID teamId);

    /** Create a new team. Returns the created team data. */
    TeamData createTeam(UUID ownerId, String name);

    /** Disband (delete) a team. Only the owner can do this. */
    boolean disbandTeam(UUID teamId, UUID requesterId);

    /** Invite a player to the team. */
    boolean invitePlayer(UUID teamId, UUID inviterId, UUID targetId);

    /** Kick a player from the team. */
    boolean kickPlayer(UUID teamId, UUID kickerId, UUID targetId);

    /** Transfer ownership to another member. */
    boolean transferOwnership(UUID teamId, UUID currentOwnerId, UUID newOwnerId);

    /** Set ally status between two teams. */
    boolean setAlly(UUID teamIdA, UUID teamIdB, boolean ally);

    /** Check if two players share a network (same team or allies). */
    boolean shareNetwork(UUID playerA, UUID playerB);

    /** Name of this provider for diagnostics. */
    String getProviderName();
}
