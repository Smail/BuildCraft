/* Copyright (c) 2017-2026 the BuildCraft team. Licensed under the Mozilla Public License, v. 2.0. */
package buildcraft.silicon;

import buildcraft.BuildCraftFabric;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftRegistries;
import buildcraft.core.BCCore;
import buildcraft.lib.CreativeTabManager;
import buildcraft.lib.CreativeTabManager.CreativeTabBC;
import buildcraft.lib.internal.module.BCModules;
import buildcraft.lib.internal.module.FabricModule;
import buildcraft.lib.platform.config.ConfigBinding;
import buildcraft.lib.platform.registry.BCDeferredRegister;
import buildcraft.lib.platform.registry.BCRegistryEntry;
import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeInstance;
import buildcraft.silicon.plug.FacadeStateManager;
import buildcraft.transport.BCTransport;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;

public final class BCSilicon implements FabricModule {
    public static final String MODID = "buildcraftsilicon";
    public static final CreativeTabBC tabPlugs = BCTransport.tabPlugs;
    public static final CreativeTabBC tabFacades = CreativeTabManager.createTab("buildcraft.facades").setRecipeFolderName("facades");
    private static final BCDeferredRegister<CreativeModeTab> CREATIVE_TABS = BCDeferredRegister.create("minecraft:creative_mode_tab", "buildcraft");
    public static final BCRegistryEntry<CreativeModeTab> FACADES_TAB = CREATIVE_TABS.register("facades", () ->
        FabricCreativeModeTab.builder().title(Component.translatable("itemGroup.buildcraft.facades"))
            .icon(tabFacades::makeIcon).displayItems((parameters, output) -> tabFacades.accept(List.of(), output::accept)).build());

    @Override
    public String getModId() { return MODID; }

    @Override
    public void onInitialize() {
        var binder = BuildCraftFabric.registries();
        BuildCraftApi.registry(BuildCraftRegistries.FACADE_MATERIAL_ADAPTERS).register(
            Identifier.fromNamespaceAndPath("buildcraft", "facade_materials/builtin"), FacadeStateManager.INSTANCE);
        BCSiliconConfig.preInit();
        ConfigBinding.register(MODID, BCSiliconConfig.config, BCSiliconConfig::onLoadConfig, BCSiliconConfig::onReloadConfig);
        BCSiliconStatements.preInit();
        BCSiliconPlugs.preInit();
        BCSiliconBlocks.registry(binder);
        BCSiliconItems.registry(binder);
        BCSiliconGuis.preInit(binder);
        BCSiliconRecipes.preInit(binder);
        CREATIVE_TABS.register(binder);
        BCCore.BUILDCRAFT_TAB.addItemProvider(BCSiliconItems::getMainTabItems);
        tabPlugs.addItemProvider(BCSiliconItems::getPlugTabItems);
        tabFacades.addItemProvider(BCSiliconItems::getFacadeTabItems);
        ServerLevelEvents.LOAD.register((server, level) -> initializeFacades());
        BuildCraftFabric.afterRegistries(() -> BCSiliconConfig.reloadConfig(MODID));
        BuildCraftFabric.afterAllMods(() -> {
            if (!BCModules.TRANSPORT.isLoaded() && BCSiliconItems.PLUG_GATE_ITEM.isBound()) tabPlugs.setItem(BCSiliconItems.PLUG_GATE_ITEM.get());
        });
    }

    public static void initializeFacades() {
        if (!BCSiliconConfig.enableFacades || !BCSiliconItems.PLUG_FACADE_ITEM.isBound()) return;
        FacadeStateManager.init();
        FacadeBlockStateInfo state = FacadeStateManager.previewState;
        if (state != null && state != FacadeStateManager.defaultState) {
            tabFacades.setItem(BCSiliconItems.PLUG_FACADE_ITEM.get().createItemStack(FacadeInstance.createSingle(state, false)));
        }
    }
}
