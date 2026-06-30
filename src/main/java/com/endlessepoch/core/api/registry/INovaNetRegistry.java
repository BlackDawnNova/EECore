package com.endlessepoch.core.api.registry;

import com.endlessepoch.core.api.component.IComponent;
import com.endlessepoch.core.api.credit.IEnergyCreditSystem;
import com.endlessepoch.core.api.field.INovaNode;
import com.endlessepoch.core.api.routing.IEnergyRouter;
import com.endlessepoch.core.api.security.IEnergySecurityProvider;
import com.endlessepoch.core.api.team.ITeamProvider;
import net.minecraft.core.BlockPos;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * NovaNet 中央注册表 —— 所有网络模块的可插拔策略模式。
 * NovaNet central registry — pluggable strategy pattern for all network modules.
 * <p>
 * EECore 提供默认实现。其他模组可在 {@code EnergySystemInitEvent} 期间
 * 注册自己的提供者来替换任意模块。
 * <p>
 * EECore provides default implementations. Other mods replace any module
 * by registering their own provider during {@code EnergySystemInitEvent}.
 */
public interface INovaNetRegistry {

    void registerNode(INovaNode node);
    void unregisterNode(INovaNode node);
    Optional<INovaNode> getNode(UUID nodeId);
    Optional<INovaNode> getNode(BlockPos pos);
    Set<INovaNode> getAllNodes();
    Set<INovaNode> getNodesInRange(BlockPos center, int radius);

    void registerSecurityProvider(IEnergySecurityProvider provider);
    IEnergySecurityProvider getSecurityProvider();

    void registerRouter(IEnergyRouter router);
    IEnergyRouter getRouter();

    void registerCreditSystem(IEnergyCreditSystem system);
    IEnergyCreditSystem getCreditSystem();

    void registerTeamProvider(ITeamProvider provider);
    ITeamProvider getTeamProvider();

    void registerComponent(IComponent component);
    Optional<IComponent> getComponent(String componentId);
    Set<IComponent> getComponents(Class<? extends IComponent> type);
}
