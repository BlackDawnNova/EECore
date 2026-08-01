package com.endlessepoch.core.nova.block;

import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.GridHelper;
import appeng.api.storage.ILinkStatus;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.MEStorage;
import appeng.api.storage.SupplierStorage;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.endlessepoch.core.registry.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dispatch center BE with full AE2 terminal host support.
 * 调度中心 BE——完整 AE2 终端宿主。
 */
public class DispatchCenterBlockEntity extends MachineControllerBlockEntity
        implements ITerminalHost, IActionHost, IConfigurableObject, ISubMenuHost {

    private final IManagedGridNode mainNode;
    private final IConfigManager configManager;

    public DispatchCenterBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        this.mainNode = GridHelper.createManagedNode(this, (be, node) -> be.setChanged())
                .setVisualRepresentation(getBlockState().getBlock().asItem())
                .setInWorldNode(true)
                .setTagName("dispatch");
        this.configManager = IConfigManager.builder(() -> {
            if (getMainNode().isReady()) getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        }).registerSetting(Settings.SORT_BY, SortOrder.NAME)
          .registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING)
          .registerSetting(Settings.VIEW_MODE, ViewItems.ALL)
          .build();
    }

    @Override public BlockEntityType<?> getType() { return BlockEntities.DISPATCH_CONTROLLER.get(); }

    // ITerminalHost
    @Override public MEStorage getInventory() {
        return new SupplierStorage(() -> {
            IGridNode node = getActionableNode();
            return node != null && node.getGrid() != null
                    ? node.getGrid().getStorageService().getInventory()
                    : new MEStorage() {
                          @Override public net.minecraft.network.chat.Component getDescription() {
                              return net.minecraft.network.chat.Component.empty();
                          }
                      };
        });
    }
    @Override public ILinkStatus getLinkStatus() { return ILinkStatus.ofManagedNode(getMainNode()); }

    // IConfigurableObject
    @Override public IConfigManager getConfigManager() { return configManager; }

    // ISubMenuHost
    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(com.endlessepoch.core.registry.Menus.DISPATCH.get(), player, MenuLocators.forBlockEntity(this));
    }
    @Override public ItemStack getMainMenuIcon() { return new ItemStack(getBlockState().getBlock().asItem()); }

    // IActionHost
    @Override public IGridNode getActionableNode() { return mainNode.getNode(); }
    public IManagedGridNode getMainNode() { return mainNode; }

    // Lifecycle
    @Override public void onLoad() { super.onLoad(); if (level != null && !level.isClientSide()) mainNode.create(level, worldPosition); }
    @Override public void setRemoved() { super.setRemoved(); mainNode.destroy(); }
    @Override public void onChunkUnloaded() { super.onChunkUnloaded(); mainNode.destroy(); }
}
