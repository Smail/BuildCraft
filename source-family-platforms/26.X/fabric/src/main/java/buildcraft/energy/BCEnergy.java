/* Copyright (c) 2016-2026 the BuildCraft team. Licensed under the Mozilla Public License, v. 2.0. */
package buildcraft.energy;

import buildcraft.BuildCraftFabric;
import buildcraft.core.BCCore;
import buildcraft.energy.tile.TileSpringOil;
import buildcraft.lib.internal.module.FabricModule;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.platform.config.ConfigBinding;
import buildcraft.lib.platform.events.BCEvents;
import buildcraft.lib.platform.events.PlatformEvents;
import buildcraft.lib.platform.registry.BCDeferredRegister;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public final class BCEnergy implements FabricModule, ModInitializer {
    public static final String MODID = "buildcraftenergy";
    public static final BCDeferredRegister<Item> ITEMS = BCDeferredRegister.create("minecraft:item", MODID);
    public static final BCDeferredRegister<MenuType<?>> MENUS = BCDeferredRegister.create("minecraft:menu", MODID);
    private static final Identifier FIND_OIL = Identifier.fromNamespaceAndPath(MODID, "fine_riches");

    @Override
    public String getModId() { return MODID; }

    @Override
    public void onInitialize() {
        var binder = BuildCraftFabric.registries();
        BCEnergyFluids.registry(binder);
        BCEnergyBlocks.init(binder);
        BCEnergyGuis.init();
        BCEnergyWorldGen.preInit(binder);
        BCEnergyConfig.preInit();
        BCCore.BUILDCRAFT_TAB.addItemProvider(BCEnergyBlocks::getCreativeTabItems);
        BCCore.tabFluids.addItemProvider(BCEnergyFluids::getCreativeTabItems);
        ConfigBinding.register(MODID, BCEnergyConfig.config, BCEnergyConfig::onLoadConfig, BCEnergyConfig::onReloadConfig);
        ITEMS.register(binder);
        MENUS.register(binder);
        PlatformEvents.playerTick(BCEvents.Phase.END, BCEnergy::onPlayerTick);
        BuildCraftFabric.afterRegistries(() -> {
            BCEnergyFluids.init();
            BCCore.tabFluids.setItem(BCEnergyFluids.OIL_BUCKET.getFirst().get());
            BCEnergyRecipes.init();
            BCEnergyConfig.reloadConfig(MODID);
        });
    }

    private static void onPlayerTick(BCEvents.PlayerTick event) {
        if (!(event.player() instanceof ServerPlayer player) || player.tickCount % 40 != 0) return;
        var server = player.level().getServer();
        if (server == null) return;
        var advancement = server.getAdvancements().get(FIND_OIL);
        if (advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone()) return;
        if (isNearOilSpot(player)) AdvancementUtil.unlockAdvancement(player, FIND_OIL);
    }

    private static boolean isNearOilSpot(ServerPlayer player) {
        Level level = player.level();
        BlockPos center = player.blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = -8; y <= 8; y++) {
            for (int x = -12; x <= 12; x++) {
                for (int z = -12; z <= 12; z++) {
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!level.isLoaded(pos)) continue;
                    Fluid fluid = level.getFluidState(pos).getType();
                    if (fluid != Fluids.EMPTY && BCEnergyFluids.crudeOil[0] != null
                            && FluidUtilBC.areFluidsEqual(fluid, BCEnergyFluids.crudeOil[0])) return true;
                    if (level.getBlockState(pos).hasBlockEntity() && level.getBlockEntity(pos) instanceof TileSpringOil) return true;
                }
            }
        }
        return false;
    }
}
