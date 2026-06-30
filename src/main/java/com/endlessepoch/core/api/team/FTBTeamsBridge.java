package com.endlessepoch.core.api.team;

import java.util.Optional;
import java.util.UUID;

/**
 * FTB Teams compatibility bridge.
 * <p>
 * When FTB Teams is loaded, this provider delegates to it.
 * When absent, falls back to {@link EECoreTeamProvider}.
 * <p>
 * Call {@link #create()} during mod construction — it auto-detects FTB Teams presence.
 * <p>
 * FTB Teams 兼容桥接器。
 * <p>
 * 当 FTB Teams 已加载时，此提供者将其委托给 FTB Teams。
 * 当不存在时，回退到 {@link EECoreTeamProvider}。
 * <p>
 * 在模组构造期间调用 {@link #create()} —— 它会自动检测 FTB Teams 是否存在。
 */
public class FTBTeamsBridge implements ITeamProvider {

    private final ITeamProvider delegate;
    private final boolean ftbPresent;

    private FTBTeamsBridge(ITeamProvider delegate, boolean ftbPresent) {
        this.delegate = delegate;
        this.ftbPresent = ftbPresent;
    }

    /**
     * Create the appropriate provider based on whether FTB Teams is present.
     * <p>
     * 根据 FTB Teams 是否存在创建合适的提供者。
     */
    public static ITeamProvider create() {
        try {
            Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            return new FTBTeamsBridge(new EECoreTeamProvider(), true);
        } catch (ClassNotFoundException e) {
            return new EECoreTeamProvider();
        }
    }

    public boolean isFTBPresent() { return ftbPresent; }

    @Override
    public Optional<TeamData> getTeam(UUID playerId) {
        return delegate.getTeam(playerId);
    }

    @Override
    public Optional<TeamData> getTeamById(UUID teamId) {
        return delegate.getTeamById(teamId);
    }

    @Override
    public TeamData createTeam(UUID ownerId, String name) {
        return delegate.createTeam(ownerId, name);
    }

    @Override
    public boolean disbandTeam(UUID teamId, UUID requesterId) {
        return delegate.disbandTeam(teamId, requesterId);
    }

    @Override
    public boolean invitePlayer(UUID teamId, UUID inviterId, UUID targetId) {
        return delegate.invitePlayer(teamId, inviterId, targetId);
    }

    @Override
    public boolean kickPlayer(UUID teamId, UUID kickerId, UUID targetId) {
        return delegate.kickPlayer(teamId, kickerId, targetId);
    }

    @Override
    public boolean transferOwnership(UUID teamId, UUID currentOwnerId, UUID newOwnerId) {
        return delegate.transferOwnership(teamId, currentOwnerId, newOwnerId);
    }

    @Override
    public boolean setAlly(UUID teamIdA, UUID teamIdB, boolean ally) {
        return delegate.setAlly(teamIdA, teamIdB, ally);
    }

    @Override
    public boolean shareNetwork(UUID playerA, UUID playerB) {
        return delegate.shareNetwork(playerA, playerB);
    }

    @Override
    public String getProviderName() {
        return ftbPresent ? "FTB Teams (via EECore)" : "EECore";
    }
}
