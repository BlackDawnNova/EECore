package com.endlessepoch.core;

import com.endlessepoch.core.api.energy.EnergyPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = EECore.MOD_ID)
public class Config {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue STEP_LOSS_FACTOR;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("EECore global config").push("general");

        STEP_LOSS_FACTOR = builder
                .comment(
                        "Energy loss factor per voltage step-down (0.0-1.0). Default 0.8 means 20% loss per step.",
                        "电压降级能量损耗因子 (0.0-1.0)，默认 0.8 表示每级损耗 20%。")
                .defineInRange("stepLossFactor", 0.8, 0.0, 1.0);

        builder.pop();
        SPEC = builder.build();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        EnergyPacket.setStepLoss(STEP_LOSS_FACTOR.get());
    }
}