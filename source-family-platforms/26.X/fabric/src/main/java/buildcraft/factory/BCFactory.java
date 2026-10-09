/* Copyright (c) 2016-2026 the BuildCraft team. Licensed under the Mozilla Public License, v. 2.0. */
package buildcraft.factory;

import buildcraft.BuildCraftFabric;
import buildcraft.core.BCCore;
import buildcraft.lib.internal.module.FabricModule;
import net.fabricmc.api.ModInitializer;

public final class BCFactory implements FabricModule, ModInitializer {
    public static final String MODID = "buildcraftfactory";

    @Override
    public String getModId() { return MODID; }

    @Override
    public void onInitialize() {
        var binder = BuildCraftFabric.registries();
        BCFactoryBlocks.registry(binder);
        BCFactoryItems.registry(binder);
        BCFactoryGuis.registry(binder);
        BCCore.BUILDCRAFT_TAB.addItemProvider(BCFactoryItems::getCreativeTabItems);
    }
}
