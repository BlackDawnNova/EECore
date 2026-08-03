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
import net.minecraft.core.Direction;
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

    /**
     * Grid via the ME port inside the structure — the port is the cable access
     * point; the controller's own node may sit on an isolated grid. No isActive
     * gate: storage viewing needs no channels, only a grid link.
     * 优先取结构内 ME 端口的网格——端口才是线缆接入点，控制器自身节点可能挂在孤岛网格。
     * 不做 isActive 拦截：查看存储不需要通道，只要求网格连通。
     */
    public appeng.api.networking.IGrid getGrid() {
        var p = findPort();
        if (p != null && level.getBlockEntity(p) instanceof com.endlessepoch.core.nova.block.part.DispatchMePortBlockEntity port) {
            var pn = port.getMainNode().getNode();
            if (pn != null && pn.getGrid() != null) return pn.getGrid();
        }
        var own = getActionableNode();
        return own != null ? own.getGrid() : null;
    }

    private net.minecraft.core.BlockPos portPos;

    /** Cached ME port position — full scan only when invalidated. / 缓存端口位置——失效时才全扫。 */
    private net.minecraft.core.BlockPos findPort() {
        if (portPos != null) {
            if (level.getBlockEntity(portPos) instanceof com.endlessepoch.core.nova.block.part.DispatchMePortBlockEntity) return portPos;
            portPos = null;
        }
        if (level == null || getMachineId() == null) return null;
        var pattern = com.endlessepoch.core.api.multiblock.MultiBlockRegistry.get(getMachineId());
        if (pattern.isEmpty()) return null;
        var pat = pattern.get();
        // Frame-based patterns have no voxel grid — scan the shell bounding box.
        // 框架式无体素栅格——扫外壳包围盒找端口。
        if (pat.isFrameBased()) {
            int r = Math.max(pat.getInnerW(), Math.max(pat.getInnerH(), pat.getInnerD())) / 2 + 2;
            for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
                BlockPos wp = worldPosition.offset(dx, dy, dz);
                if (level.getBlockEntity(wp) instanceof com.endlessepoch.core.nova.block.part.DispatchMePortBlockEntity) {
                    portPos = wp;
                    return wp;
                }
            }
            return null;
        }
        Direction facing = getFacing();
        for (BlockPos localPos : pat.getNonAirPositions()) {
            BlockPos wp = com.endlessepoch.core.api.multiblock.MultiBlockValidator.fromLocal(
                    worldPosition, localPos.getX(), localPos.getY(), localPos.getZ(), facing,
                    pat.controllerX, pat.controllerY, pat.controllerZ);
            if (level.getBlockEntity(wp) instanceof com.endlessepoch.core.nova.block.part.DispatchMePortBlockEntity) {
                portPos = wp;
                return wp;
            }
        }
        return null;
    }

    // ITerminalHost / 终端宿主接口
    @Override public MEStorage getInventory() {
        return new SupplierStorage(() -> {
            var grid = getGrid();
            return grid != null
                    ? grid.getStorageService().getInventory()
                    : new MEStorage() {
                          @Override public net.minecraft.network.chat.Component getDescription() {
                              return net.minecraft.network.chat.Component.empty();
                          }
                      };
        });
    }
    @Override public ILinkStatus getLinkStatus() { return ILinkStatus.ofManagedNode(getMainNode()); }

    // IConfigurableObject / 可配置对象接口
    @Override public IConfigManager getConfigManager() { return configManager; }

    // ISubMenuHost / 子菜单宿主接口
    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(com.endlessepoch.core.registry.Menus.DISPATCH.get(), player, MenuLocators.forBlockEntity(this));
    }
    @Override public ItemStack getMainMenuIcon() { return new ItemStack(getBlockState().getBlock().asItem()); }

    // IActionHost / 动作宿主接口
    @Override public IGridNode getActionableNode() { return mainNode.getNode(); }
    public IManagedGridNode getMainNode() { return mainNode; }

    // Lifecycle / 生命周期
    @Override public void onLoad() { super.onLoad(); if (level != null && !level.isClientSide()) mainNode.create(level, worldPosition); }
    @Override public void setRemoved() { super.setRemoved(); mainNode.destroy(); }
    @Override public void onChunkUnloaded() { super.onChunkUnloaded(); mainNode.destroy(); }
}
