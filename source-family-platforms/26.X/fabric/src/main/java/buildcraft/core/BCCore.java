/* Copyright (c) 2016-2026 the BuildCraft team. Licensed under the Mozilla Public License, v. 2.0. */
package buildcraft.core;

import buildcraft.BuildCraftFabric;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftRegistries;
import buildcraft.core.list.ContainerList;
import buildcraft.core.marker.PathCache;
import buildcraft.core.marker.VolumeCache;
import buildcraft.core.marker.volume.MessageVolumeBoxes;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.energy.tile.TileSpringOil;
import buildcraft.lib.CreativeTabManager;
import buildcraft.lib.CreativeTabManager.CreativeTabBC;
import buildcraft.lib.gui.BCContainerFactory;
import buildcraft.lib.internal.enums.EnumSpring;
import buildcraft.lib.internal.module.BCModules;
import buildcraft.lib.internal.module.FabricModule;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.net.MessageManager;
import buildcraft.lib.platform.config.ConfigBinding;
import buildcraft.lib.platform.registry.BCDeferredRegister;
import buildcraft.lib.platform.registry.BCRegistryEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;

public final class BCCore implements FabricModule {
    public static final String MODID = "buildcraftcore";
    public static final CreativeTabBC BUILDCRAFT_TAB = CreativeTabManager.createTab("buildcraft.main");
    public static final CreativeTabBC tabFluids = CreativeTabManager.createTab("buildcraft.fluid");
    public static final Map<String, Object> ENGINE_MAP = new HashMap<>();
    private static final BCDeferredRegister<CreativeModeTab> CREATIVE_TABS = BCDeferredRegister.create("minecraft:creative_mode_tab", "buildcraft");
    public static final BCRegistryEntry<CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register("main", () ->
        FabricCreativeModeTab.builder().title(Component.translatable("itemGroup.buildcraft.main"))
            .icon(BUILDCRAFT_TAB::makeIcon).displayItems((parameters, output) -> BUILDCRAFT_TAB.accept(List.of(), output::accept)).build());
    public static final BCRegistryEntry<CreativeModeTab> FLUID_TAB = CREATIVE_TABS.register("fluid", () ->
        FabricCreativeModeTab.builder().title(Component.translatable("itemGroup.buildcraft.fluid"))
            .icon(tabFluids::makeIcon).displayItems((parameters, output) -> tabFluids.accept(List.of(), output::accept)).build());
    public static final BCDeferredRegister<MenuType<?>> MENUS = BCDeferredRegister.create("minecraft:menu", MODID);
    public static final BCRegistryEntry<MenuType<ContainerList>> LIST_MENU = MENUS.register("list_menu", () -> BCContainerFactory.create(ContainerList::new));

    @Override
    public String getModId() { return MODID; }

    @Override
    public void onInitialize() {
        var binder = BuildCraftFabric.registries();
        BCCoreBlocks.registry(binder);
        BCCoreItems.registry(binder);
        BUILDCRAFT_TAB.addItemProvider(BCCoreItems::getCreativeTabItems);
        CREATIVE_TABS.register(binder);
        MENUS.register(binder);
        BCCoreConfig.registry();
        ConfigBinding.register(MODID, BCCoreConfig.config, BCCoreConfig::onLoadConfig, BCCoreConfig::onReloadConfig);
        MessageManager.registerClientboundMessageClass(BCModules.CORE, MessageVolumeBoxes.class,
            MessageVolumeBoxes.HANDLER, MessageVolumeBoxes::toBytes, MessageVolumeBoxes::new);
        BCCoreStatements.preInit();
        BuildCraftFabric.afterRegistries(BCCore::commonSetup);
    }

    private static void commonSetup() {
        MarkerCache.registerCache(VolumeCache.INSTANCE);
        MarkerCache.registerCache(PathCache.INSTANCE);
        EnumSpring.OIL.liquidBlock = BCEnergyFluids.OIL_BLOCK.getFirst().get().defaultBlockState();
        EnumSpring.OIL.tileConstructor = TileSpringOil::new;
        BCCoreConfig.reloadConfig(MODID);
        BUILDCRAFT_TAB.setItem(BCCoreItems.WRENCH.get());
        BuildCraftApi.registry(BuildCraftRegistries.FLUID_DROP_PROVIDERS).register(
            Identifier.fromNamespaceAndPath(MODID, "fragile_fluid_shard"), BCCoreItems.FRAGILE_FLUID_SHARD.get());
    }
}
