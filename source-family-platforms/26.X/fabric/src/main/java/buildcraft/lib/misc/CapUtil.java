//? source if >=26.3
package buildcraft.lib.misc;

import javax.annotation.Nullable;
import buildcraft.lib.fluid.BCFluidHandler;
import buildcraft.lib.fluid.BCFluidStack;
import buildcraft.lib.platform.capability.BCBlockCapability;
import buildcraft.lib.platform.capability.BCEntityCapability;
import buildcraft.lib.platform.capability.BCCapabilities;
import buildcraft.lib.platform.storage.EnergyStorage;
import buildcraft.lib.platform.storage.FluidStorage;
import buildcraft.lib.platform.storage.ItemStorage;
import buildcraft.lib.platform.storage.PlatformStorage;
import buildcraft.lib.internal.inventory.IItemTransactor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Lookup boundary uses Fabric storage first, then the internal provider contract. */
public final class CapUtil {
    public static final BCBlockCapability<ItemStorage, Direction> CAP_ITEMS = BCCapabilities.ItemHandler.BLOCK;
    public static final BCBlockCapability<BCFluidHandler, Direction> CAP_FLUIDS = BCCapabilities.FluidHandler.BLOCK;
    public static final BCBlockCapability<EnergyStorage, Direction> CAP_FE = BCCapabilities.EnergyStorage.BLOCK;
    public static final BCBlockCapability<IItemTransactor, Direction> CAP_ITEM_TRANSACTOR = BCBlockCapability.createSided(id("item_transactor"), IItemTransactor.class);
    public static final BCEntityCapability<IItemTransactor, Direction> CAP_ITEM_TRANSACTOR_ENTITY = BCEntityCapability.createSided(id("item_transactor"), IItemTransactor.class);
    private CapUtil() {}
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("buildcraftlib", path); }
    @Nullable public static ItemStorage getItemHandler(@Nullable Level level, @Nullable BlockPos pos, @Nullable Direction face) {
        ItemStorage nativeStorage = PlatformStorage.itemSlots(level, pos, face);
        return nativeStorage != null ? nativeStorage : CAP_ITEMS.getCapability(level, pos, face);
    }
    @Nullable public static ItemStorage getItemHandler(@Nullable Entity entity, @Nullable Direction face) { return PlatformStorage.itemSlots(entity, face); }
    @Nullable public static BCFluidHandler getFluidHandler(@Nullable Level level, @Nullable BlockPos pos, @Nullable Direction face) {
        BCFluidHandler storage = PlatformStorage.legacyFluids(level, pos, face);
        return storage != null ? storage : CAP_FLUIDS.getCapability(level, pos, face);
    }
    @Nullable public static EnergyStorage getEnergyStorage(@Nullable Level level, @Nullable BlockPos pos, @Nullable Direction face) {
        EnergyStorage storage = PlatformStorage.energy(level, pos, face);
        return storage != null ? storage : CAP_FE.getCapability(level, pos, face);
    }
    @Nullable public static <T, C> T getCapability(@Nullable Level level, @Nullable BlockPos pos, @Nullable BCBlockCapability<T, C> capability, @Nullable C context) {
        if (capability == null || level == null || pos == null) return null;
        if (context == null || context instanceof Direction) {
            Direction face = (Direction) context;
            if (capability == CAP_ITEMS) return (T) getItemHandler(level, pos, face);
            if (capability == CAP_FLUIDS) return (T) getFluidHandler(level, pos, face);
            if (capability == CAP_FE) return (T) getEnergyStorage(level, pos, face);
        }
        return capability.getCapability(level, pos, context);
    }
    @Nullable public static <T, C> T getCapability(@Nullable Entity entity, @Nullable BCEntityCapability<T, C> capability, @Nullable C context) {
        return capability == null ? null : capability.getCapability(entity, context);
    }
}
