package com.endlessepoch.core.registry;

import com.endlessepoch.core.EECore;
import com.endlessepoch.core.menu.BusMenu;
import com.endlessepoch.core.menu.HatchMenu;
import com.endlessepoch.core.menu.MachineMenu;
import com.endlessepoch.core.menu.creative.CreativeConsumerMenu;
import com.endlessepoch.core.menu.creative.CreativeGeneratorMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class Menus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, EECore.MOD_ID);

    public static final Supplier<MenuType<CreativeGeneratorMenu>> CREATIVE_GENERATOR =
            MENUS.register("creative_generator",
                    () -> IMenuTypeExtension.create(CreativeGeneratorMenu::new)
            );

    public static final Supplier<MenuType<CreativeConsumerMenu>> CREATIVE_CONSUMER =
            MENUS.register("creative_consumer",
                    () -> IMenuTypeExtension.create(CreativeConsumerMenu::new)
            );

    public static final Supplier<MenuType<BusMenu>> BUS =
            MENUS.register("bus",
                    () -> IMenuTypeExtension.create(BusMenu::new)
            );

    public static final Supplier<MenuType<MachineMenu>> MACHINE =
            MENUS.register("machine",
                    () -> IMenuTypeExtension.create(MachineMenu::new)
            );

    public static final Supplier<MenuType<HatchMenu>> HATCH =
            MENUS.register("hatch",
                    () -> IMenuTypeExtension.create(HatchMenu::new)
            );
}