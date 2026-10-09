/* Copyright (c) 2017-2026 the BuildCraft team. Licensed under the Mozilla Public License, v. 2.0. */
package buildcraft.transport;

import buildcraft.BuildCraftFabric;
import buildcraft.builders.internal.schematic.legacy.SchematicBlockFactoryRegistry;
import buildcraft.core.BCCore;
import buildcraft.lib.BCLibRegistries;
import buildcraft.lib.CreativeTabManager;
import buildcraft.lib.CreativeTabManager.CreativeTabBC;
import buildcraft.lib.internal.module.BCModules;
import buildcraft.lib.internal.module.FabricModule;
import buildcraft.lib.net.MessageManager;
import buildcraft.lib.platform.config.ConfigBinding;
import buildcraft.lib.platform.registry.BCDeferredRegister;
import buildcraft.lib.platform.registry.BCRegistryEntry;
import buildcraft.transport.api2.TransportApi2;
import buildcraft.transport.net.MessageMultiPipeItem;
import buildcraft.transport.pipe.SchematicBlockPipe;
import buildcraft.transport.wire.MessageWireSystems;
import buildcraft.transport.wire.MessageWireSystemsPowered;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;

public final class BCTransport implements FabricModule {
    public static final String MODID = "buildcrafttransport";
    public static final CreativeTabBC tabPipes = CreativeTabManager.createTab("buildcraft.pipes").setRecipeFolderName("pipes");
    public static final CreativeTabBC tabPlugs = CreativeTabManager.createTab("buildcraft.plugs").setRecipeFolderName("plugs");
    private static final BCDeferredRegister<CreativeModeTab> CREATIVE_TABS = BCDeferredRegister.create("minecraft:creative_mode_tab", "buildcraft");
    public static final BCRegistryEntry<CreativeModeTab> PIPES_TAB = CREATIVE_TABS.register("pipes", () ->
        FabricCreativeModeTab.builder().title(Component.translatable("itemGroup.buildcraft.pipes"))
            .icon(tabPipes::makeIcon).displayItems((parameters, output) -> tabPipes.accept(List.of(), output::accept)).build());
    public static final BCRegistryEntry<CreativeModeTab> PLUGS_TAB = CREATIVE_TABS.register("plugs", () ->
        FabricCreativeModeTab.builder().title(Component.translatable("itemGroup.buildcraft.plugs"))
            .icon(tabPlugs::makeIcon).displayItems((parameters, output) -> tabPlugs.accept(List.of(), output::accept)).build());

    @Override
    public String getModId() { return MODID; }

    @Override
    public void onInitialize() {
        var binder = BuildCraftFabric.registries();
        BCLibRegistries.initApiRegistries();
        TransportApi2.install();
        BCTransportRegistries.preInit();
        BCTransportConfig.preInit();
        BCTransportRecipes.preInit(binder);
        BCTransportPipes.preInit();
        BCTransportPlugs.preInit();
        BCTransportBlocks.registry(binder);
        BCTransportItems.registry(binder);
        tabPipes.addItemProvider(BCTransportItems::getPipeTabItems);
        tabPlugs.addItemProvider(BCTransportItems::getPlugTabItems);
        BCCore.BUILDCRAFT_TAB.addItemProvider(BCTransportBlocks::getCreativeTabItems);
        BCTransportGuis.preInit(binder);
        CREATIVE_TABS.register(binder);
        BCTransportStatements.preInit();
        ConfigBinding.register(MODID, BCTransportConfig.config, BCTransportConfig::onConfigLoad, BCTransportConfig::onConfigReload);
        MessageManager.registerClientboundMessageClass(BCModules.TRANSPORT, MessageWireSystems.class,
            MessageWireSystems.HANDLER, MessageWireSystems::toBytes, MessageWireSystems::new);
        MessageManager.registerClientboundMessageClass(BCModules.TRANSPORT, MessageWireSystemsPowered.class,
            MessageWireSystemsPowered.HANDLER, MessageWireSystemsPowered::toBytes, MessageWireSystemsPowered::new);
        MessageManager.registerClientboundMessageClass(BCModules.TRANSPORT, MessageMultiPipeItem.class,
            MessageMultiPipeItem.HANDLER, MessageMultiPipeItem::toBytes, MessageMultiPipeItem::new);
        BCTransportEventDist.registerGameplayEvents();
        SchematicBlockFactoryRegistry.registerFactory("pipe", 300, SchematicBlockPipe::predicate, SchematicBlockPipe::new);
        BuildCraftFabric.afterRegistries(() -> {
            BCTransportConfig.reloadConfig();
            BCTransportRegistries.init();
            tabPipes.setItem(BCTransportItems.PIPE_ITEM_DIAMOND.get());
            tabPlugs.setItem(BCTransportItems.plugBlocker.get());
        });
    }
}
