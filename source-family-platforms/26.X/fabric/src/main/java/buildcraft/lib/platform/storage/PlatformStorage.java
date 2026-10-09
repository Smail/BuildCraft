package buildcraft.lib.platform.storage;

import java.util.Objects;

import javax.annotation.Nullable;

import buildcraft.lib.fluid.BCFluidStack;
import buildcraft.lib.fluid.FabricFluidStack;
import buildcraft.lib.inventory.FabricItemTransactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

/** Fresh native lookups. Arbitrary item stores use a transactor rather than synthetic slots. */
public final class PlatformStorage {
    public static final EntityApiLookup<Storage<ItemVariant>, Direction> ENTITY_ITEMS = EntityApiLookup.get(
        Identifier.fromNamespaceAndPath("buildcraftlib", "item_storage"), Storage.asClass(), Direction.class);

    private PlatformStorage() {}

    public static ItemStorage localInventory(Container inventory) {
        return StorageAdapters.fromNativeItems(ContainerStorage.of(Objects.requireNonNull(inventory, "inventory"), null));
    }

    @Nullable
    public static ItemStorage itemSlots(@Nullable Level level, @Nullable BlockPos pos, @Nullable Direction face) {
        Storage<ItemVariant> storage = items(level, pos, face);
        return storage instanceof net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage<ItemVariant> slots
            ? StorageAdapters.fromNativeItems(slots) : null;
    }

    @Nullable
    public static ItemStorage itemSlots(@Nullable Entity entity, @Nullable Direction face) {
        Storage<ItemVariant> storage = items(entity, face);
        return storage instanceof net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage<ItemVariant> slots
            ? StorageAdapters.fromNativeItems(slots) : null;
    }

    @Nullable
    public static Storage<ItemVariant> items(@Nullable Level level, @Nullable BlockPos pos, @Nullable Direction face) {
        return level == null || pos == null ? null
            : net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, pos, face);
    }

    @Nullable
    public static Storage<ItemVariant> items(@Nullable Entity entity, @Nullable Direction face) {
        if (entity == null) {
            return null;
        }
        Storage<ItemVariant> storage = ENTITY_ITEMS.find(entity, face);
        if (storage != null) {
            return storage;
        }
        return entity instanceof Container container ? ContainerStorage.of(container, face) : null;
    }

    @Nullable
    public static FabricItemTransactor itemTransactor(@Nullable Level level, @Nullable BlockPos pos, @Nullable Direction face) {
        Storage<ItemVariant> storage = items(level, pos, face);
        return storage == null ? null : new FabricItemTransactor(storage);
    }

    @Nullable
    public static FabricItemTransactor itemTransactor(@Nullable Entity entity, @Nullable Direction face) {
        Storage<ItemVariant> storage = items(entity, face);
        return storage == null ? null : new FabricItemTransactor(storage);
    }

    @Nullable
    public static FluidStorage<FabricFluidStack> nativeFluids(@Nullable Level level, @Nullable BlockPos pos, @Nullable Direction face) {
        return level == null || pos == null ? null : StorageAdapters.fromNativeFluids(
            net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(level, pos, face));
    }

    /** Gameplay-facing view over the native lookup. Gameplay code uses the mutable {@link BCFluidStack}. */
    @Nullable
    public static FluidStorage<BCFluidStack> fluids(@Nullable Level level, @Nullable BlockPos pos, @Nullable Direction face) {
        FluidStorage<FabricFluidStack> storage = nativeFluids(level, pos, face);
        return storage == null ? null : new BCFluidStorageView(storage);
    }

    @Nullable
    public static buildcraft.lib.fluid.BCFluidHandler legacyFluids(@Nullable Level level, @Nullable BlockPos pos, @Nullable Direction face) {
        FluidStorage<FabricFluidStack> storage = nativeFluids(level, pos, face);
        if (storage == null) return null;
        return new buildcraft.lib.fluid.BCFluidHandler() {
            public int getTanks() { return storage.getTanks(); }
            public buildcraft.lib.fluid.BCFluidStack getFluidInTank(int tank) { return buildcraft.lib.fluid.BCFluidStack.fromNative(storage.getFluidInTank(tank)); }
            public int getTankCapacity(int tank) { return storage.getTankCapacity(tank); }
            public boolean isFluidValid(int tank, buildcraft.lib.fluid.BCFluidStack stack) { return storage.isFluidValid(tank, stack.toNative()); }
            public int fill(buildcraft.lib.fluid.BCFluidStack stack, FluidAction action) { return storage.fill(stack.toNative(), action.simulate()); }
            public buildcraft.lib.fluid.BCFluidStack drain(buildcraft.lib.fluid.BCFluidStack stack, FluidAction action) { return buildcraft.lib.fluid.BCFluidStack.fromNative(storage.drain(stack.toNative(), action.simulate())); }
            public buildcraft.lib.fluid.BCFluidStack drain(int maximum, FluidAction action) { return buildcraft.lib.fluid.BCFluidStack.fromNative(storage.drain(maximum, action.simulate())); }
        };
    }

    @Nullable
    public static Storage<FluidVariant> fluids(ContainerItemContext context) {
        return Objects.requireNonNull(context, "context").find(net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.ITEM);
    }

    @Nullable
    public static EnergyStorage energy(@Nullable Level level, @Nullable BlockPos pos, @Nullable Direction face) {
        return level == null || pos == null ? null
            : StorageAdapters.fromNativeEnergy(team.reborn.energy.api.EnergyStorage.SIDED.find(level, pos, face));
    }

    @Nullable
    public static EnergyStorage energy(ContainerItemContext context) {
        return StorageAdapters.fromNativeEnergy(Objects.requireNonNull(context, "context")
            .find(team.reborn.energy.api.EnergyStorage.ITEM));
    }

    @Nullable
    public static EnergyStorage energy(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : energy(FabricStackContext.of(stack));
    }
}
