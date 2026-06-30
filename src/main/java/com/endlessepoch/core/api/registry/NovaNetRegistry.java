package com.endlessepoch.core.api.registry;

import com.endlessepoch.core.api.component.IComponent;
import com.endlessepoch.core.api.credit.IEnergyCreditSystem;
import com.endlessepoch.core.api.field.INovaNode;
import com.endlessepoch.core.api.routing.IEnergyRouter;
import com.endlessepoch.core.api.security.IEnergySecurityProvider;
import com.endlessepoch.core.api.team.ITeamProvider;
import net.minecraft.core.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default NovaNet registry implementation.
 * Thread-safe, push-mode — nodes register/unregister on place/break.
 */
public class NovaNetRegistry implements INovaNetRegistry {

    private final Map<UUID, INovaNode> nodesById = new ConcurrentHashMap<>();
    private final Map<BlockPos, INovaNode> nodesByPos = new ConcurrentHashMap<>();
    private final Map<String, IComponent> components = new ConcurrentHashMap<>();

    private IEnergySecurityProvider securityProvider;
    private IEnergyRouter router;
    private IEnergyCreditSystem creditSystem;
    private ITeamProvider teamProvider;

    // ===== Node management =====

    @Override
    public void registerNode(INovaNode node) {
        nodesById.put(node.getNodeId(), node);
        nodesByPos.put(node.getBlockPos(), node);
    }

    @Override
    public void unregisterNode(INovaNode node) {
        nodesById.remove(node.getNodeId());
        nodesByPos.remove(node.getBlockPos());
    }

    @Override
    public Optional<INovaNode> getNode(UUID nodeId) {
        return Optional.ofNullable(nodesById.get(nodeId));
    }

    @Override
    public Optional<INovaNode> getNode(BlockPos pos) {
        return Optional.ofNullable(nodesByPos.get(pos));
    }

    @Override
    public Set<INovaNode> getAllNodes() {
        return Set.copyOf(nodesById.values());
    }

    @Override
    public Set<INovaNode> getNodesInRange(BlockPos center, int radius) {
        int r2 = radius * radius;
        return nodesByPos.values().stream()
                .filter(n -> n.getBlockPos().distSqr(center) <= r2)
                .collect(Collectors.toSet());
    }

    // ===== Strategy providers =====

    @Override
    public void registerSecurityProvider(IEnergySecurityProvider provider) {
        this.securityProvider = provider;
    }

    @Override
    public IEnergySecurityProvider getSecurityProvider() {
        return securityProvider;
    }

    @Override
    public void registerRouter(IEnergyRouter router) {
        this.router = router;
    }

    @Override
    public IEnergyRouter getRouter() {
        return router;
    }

    @Override
    public void registerCreditSystem(IEnergyCreditSystem system) {
        this.creditSystem = system;
    }

    @Override
    public IEnergyCreditSystem getCreditSystem() {
        return creditSystem;
    }

    @Override
    public void registerTeamProvider(ITeamProvider provider) {
        this.teamProvider = provider;
    }

    @Override
    public ITeamProvider getTeamProvider() {
        return teamProvider;
    }

    // ===== Component system =====

    @Override
    public void registerComponent(IComponent component) {
        components.put(component.getComponentId(), component);
    }

    @Override
    public Optional<IComponent> getComponent(String componentId) {
        return Optional.ofNullable(components.get(componentId));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<IComponent> getComponents(Class<? extends IComponent> type) {
        return components.values().stream()
                .filter(type::isInstance)
                .collect(Collectors.toSet());
    }
}
