package com.endlessepoch.core.screen;

import com.endlessepoch.core.menu.MachineMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class MachineTestScreen extends MachineScreen<MachineMenu> {
    public MachineTestScreen(MachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, pickName(menu));
    }

    private static Component pickName(MachineMenu menu) {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        return Component.literal(lang != null && lang.toLowerCase().contains("zh") ? menu.getNameZh() : menu.getNameEn());
    }
}

