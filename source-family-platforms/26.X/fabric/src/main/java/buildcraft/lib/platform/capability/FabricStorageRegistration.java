//? source if >=26.3
package buildcraft.lib.platform.capability;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import buildcraft.lib.compat.transfer.TransferJournal;
import buildcraft.lib.fluid.BCFluidHandler;
import buildcraft.lib.fluid.LegacyFluidExport;
import buildcraft.lib.inventory.ItemStackStorage;
import buildcraft.lib.internal.capabilities.IBCCapabilityProvider;
import buildcraft.lib.internal.debug.BCLog;
import buildcraft.lib.misc.CapUtil;
import buildcraft.lib.platform.storage.EnergyStorage;
import buildcraft.lib.platform.storage.FabricEnergyStorage;
import buildcraft.lib.platform.storage.FabricItemStorage;
import buildcraft.lib.platform.storage.ItemStorage;
import buildcraft.lib.platform.storage.StorageAdapters;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Publishes BuildCraft block entity storages through Fabric's native item, fluid and energy lookups so other mods
 * (and BuildCraft's own native-first lookups in {@code CapUtil}) can reach them.
 *
 * <p>Native exports need the owner's {@link TransferJournal}. Storage types that cannot supply one are not exported;
 * each such type is logged once.</p>
 */
public final class FabricStorageRegistration implements Runnable {
    private static final Set<Class<?>> WARNED = ConcurrentHashMap.newKeySet();

    @Override
    public void run() {
        net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.registerFallback(FabricStorageRegistration::items);
        FluidStorage.SIDED.registerFallback(FabricStorageRegistration::fluids);
        team.reborn.energy.api.EnergyStorage.SIDED.registerFallback(FabricStorageRegistration::energy);
    }

    @Nullable
    private static IBCCapabilityProvider provider(@Nullable BlockEntity entity) {
        return entity instanceof IBCCapabilityProvider provider && !entity.isRemoved() ? provider : null;
    }

    @Nullable
    private static net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant> items(
        Level level, BlockPos pos, BlockState state, @Nullable BlockEntity entity, @Nullable Direction side) {
        IBCCapabilityProvider provider = provider(entity);
        if (provider == null) return null;
        try {
            ItemStorage storage = provider.getCapability(CapUtil.CAP_ITEMS, side);
            if (storage == null) return null;
            if (storage instanceof FabricItemStorage) {
                return StorageAdapters.toNativeItems(storage, null);
            }
            if (storage instanceof ItemStackStorage stacks) {
                return StorageAdapters.toNativeItems(storage, stacks.transferJournal());
            }
            warnOnce(storage, "item");
            return null;
        } catch (RuntimeException e) {
            BCLog.logger.error("Failed to export item storage of {} at {}", entity, pos, e);
            return null;
        }
    }

    @Nullable
    private static net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant> fluids(
        Level level, BlockPos pos, BlockState state, @Nullable BlockEntity entity, @Nullable Direction side) {
        IBCCapabilityProvider provider = provider(entity);
        if (provider == null) return null;
        try {
            BCFluidHandler handler = provider.getCapability(CapUtil.CAP_FLUIDS, side);
            if (handler == null) return null;
            try {
                return LegacyFluidExport.nativeStorage(handler);
            } catch (IllegalArgumentException unsupported) { buildcraft.lib.internal.debug.BCLog.caught("FabricStorageRegistration.fluids", unsupported);
                warnOnce(handler, "fluid");
                return null;
            }
        } catch (RuntimeException e) {
            BCLog.logger.error("Failed to export fluid storage of {} at {}", entity, pos, e);
            return null;
        }
    }

    @Nullable
    private static team.reborn.energy.api.EnergyStorage energy(
        Level level, BlockPos pos, BlockState state, @Nullable BlockEntity entity, @Nullable Direction side) {
        IBCCapabilityProvider provider = provider(entity);
        if (provider == null) return null;
        try {
            EnergyStorage storage = provider.getCapability(CapUtil.CAP_FE, side);
            if (storage == null) return null;
            if (storage instanceof FabricEnergyStorage imported) {
                return imported.nativeStorage();
            }
            warnOnce(storage, "energy");
            return null;
        } catch (RuntimeException e) {
            BCLog.logger.error("Failed to export energy storage of {} at {}", entity, pos, e);
            return null;
        }
    }

    private static void warnOnce(Object storage, String kind) {
        if (WARNED.add(storage.getClass())) {
            BCLog.logger.warn("Not exporting {} storage {} to Fabric lookups: it has no transfer journal",
                kind, storage.getClass().getName());
        }
    }
}
