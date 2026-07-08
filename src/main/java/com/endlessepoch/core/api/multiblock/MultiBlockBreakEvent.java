package com.endlessepoch.core.api.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

import java.util.UUID;

/**
 * Fired when a formed multiblock is broken (any cause: player, explosion, etc.).
 * <p>
 * Other mods can listen for alert systems, repair reminders, or insurance logic.
 * 多方块结构被破坏时触发（玩家、爆炸等任何原因）。
 * 其他模组可以监听以实现警报系统、维修提醒或保险逻辑。
 */
public class MultiBlockBreakEvent extends Event {

    private final IMultiBlockController controller;
    private final BlockPos pos;
    private final Level level;

    public MultiBlockBreakEvent(IMultiBlockController controller, BlockPos pos, Level level) {
        this.controller = controller;
        this.pos = pos;
        this.level = level;
    }

    public IMultiBlockController getController() { return controller; }
    public BlockPos getPos() { return pos; }
    public Level getLevel() { return level; }

    /** Build a chat notification for the owner. / 为所有者构建聊天通知。 */
    public Component buildNotification() {
        return Component.translatable("eecore.multiblock.broken",
                pos.getX(), pos.getY(), pos.getZ(),
                level.dimension().location().toString());
    }
}
