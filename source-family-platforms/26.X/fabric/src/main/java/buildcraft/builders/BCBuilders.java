/* Copyright (c) 2016-2026 the BuildCraft team. Licensed under the Mozilla Public License, v. 2.0. */
package buildcraft.builders;

import buildcraft.BuildCraftFabric;
import buildcraft.builders.snapshot.MessageSnapshotRequest;
import buildcraft.builders.snapshot.MessageSnapshotResponse;
import buildcraft.builders.snapshot.RulesLoader;
import buildcraft.core.BCCore;
import buildcraft.lib.internal.module.BCModules;
import buildcraft.lib.internal.module.FabricModule;
import buildcraft.lib.net.BCNetworkSide;
import buildcraft.lib.net.MessageManager;
import buildcraft.lib.platform.config.ConfigBinding;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;

public final class BCBuilders implements FabricModule {
    public static final String MODID = "buildcraftbuilders";

    @Override
    public String getModId() { return MODID; }

    @Override
    public void onInitialize() {
        var binder = BuildCraftFabric.registries();
        BCBuildersBlocks.registry(binder);
        BCBuildersItems.registry(binder);
        BCCore.BUILDCRAFT_TAB.addItemProvider(BCBuildersItems::getCreativeTabItems);
        BCBuildersSchematics.preInit();
        BCBuildersConfig.preInit();
        BCBuildersRegistries.preInit();
        BCBuildersGuis.preInit(binder);
        ConfigBinding.register(MODID, BCBuildersConfig.config, BCBuildersConfig::onLoadConfig, BCBuildersConfig::onReloadConfig);
        MessageManager.registerMessageClass(BCModules.BUILDERS, MessageSnapshotRequest.class,
            MessageSnapshotRequest.HANDLER, MessageSnapshotRequest::toBytes, MessageSnapshotRequest::new, BCNetworkSide.SERVER);
        MessageManager.registerClientboundMessageClass(BCModules.BUILDERS, MessageSnapshotResponse.class,
            MessageSnapshotResponse.HANDLER, MessageSnapshotResponse::toBytes, MessageSnapshotResponse::new);
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) BCBuildersEventDist.registerGameplayEvents();
        BCBuildersStatements.preInit();
        BuildCraftFabric.afterRegistries(() -> {
            BCBuildersConfig.reloadConfig(MODID);
            BCBuildersRegistries.init();
            RulesLoader.loadAll();
        });
    }
}
