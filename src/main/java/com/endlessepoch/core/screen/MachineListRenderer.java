package com.endlessepoch.core.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

final class MachineListRenderer {

    private MachineListRenderer() {}

    static void render(GuiGraphics g, Font font, DispatchScreen screen,
                       int x, int y, int width, int mx, int my,
                       List<DispatchScreen.MachineEntry> machines, int scroll, int vis,
                       int nameDX, int statusDX, int sbDX, int sbDY) {
        int maxM = Math.max(0, machines.size() - vis);
        int sbX = x + width - 5 + sbDX, sbY = y - 1 + sbDY, sbH = vis * 18;
        if (sbH <= 18) { g.blit(DispatchScreen.SPRITES, sbX, sbY, 164, 0, 5, 9, 512, 512); g.blit(DispatchScreen.SPRITES, sbX, sbY + 9, 164, 27, 5, 9, 512, 512); }
        else if (sbH <= 36) g.blit(DispatchScreen.SPRITES, sbX, sbY, 164, 0, 5, 36, 512, 512);
        else {
            g.blit(DispatchScreen.SPRITES, sbX, sbY, 164, 0, 5, 18, 512, 512);
            for (int sy = sbY + 18; sy < sbY + sbH - 18; sy += 18) g.blit(DispatchScreen.SPRITES, sbX, sy, 164, 36, 5, 18, 512, 512);
            g.blit(DispatchScreen.SPRITES, sbX, sbY + sbH - 18, 164, 18, 5, 18, 512, 512);
        }
        if (maxM > 0) {
            int hY = sbY + (int) ((long) (sbH - 15) * scroll / maxM);
            g.blit(DispatchScreen.SPRITES, sbX - 1, hY, 172, 0, 7, 15, 512, 512);
        }
        for (int i = 0; i < Math.min(vis, machines.size()); i++) {
            int mi = scroll + i;
            if (mi >= machines.size()) break;
            int ry = y + i * 18;
            if (DispatchUtil.hit(mx, my, x, ry, width, 16))
                g.fill(x, ry, x + width, ry + 16, 0x33_66AAFF);
            var m = machines.get(mi);
            int sc = m.status() == 1 ? DispatchScreen.C_G : (m.status() == 2 ? DispatchScreen.C_R : DispatchScreen.C_TD);
            g.fill(x + 130 + statusDX, ry + 5, x + 136 + statusDX, ry + 11, sc);
            g.drawString(font, m.name(), x + 6 + nameDX, ry + 4, m.status() == 2 ? DispatchScreen.C_TD : DispatchScreen.C_T, false);
            g.drawString(font, net.minecraft.network.chat.Component.translatable(m.status() == 1 ? "eecore.dispatch.machine.running" : (m.status() == 2 ? "eecore.dispatch.machine.offline" : "eecore.dispatch.machine.idle")), x + 140 + statusDX, ry + 4, DispatchScreen.C_TD, false);
        }
    }

    static int maxScroll(List<DispatchScreen.MachineEntry> machines, int vis) {
        return Math.max(0, machines.size() - vis);
    }
}
