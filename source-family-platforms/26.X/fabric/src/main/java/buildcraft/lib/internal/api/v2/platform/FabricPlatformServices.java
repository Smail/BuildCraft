package buildcraft.lib.internal.api.v2.platform;

import java.util.Objects;
import java.util.Optional;

import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.fluid.*;
import buildcraft.api.v2.item.*;
import buildcraft.api.v2.platform.*;
import buildcraft.lib.fluid.FabricFluidVariants;
import buildcraft.lib.inventory.FabricItemTransactor;
import buildcraft.lib.platform.storage.FabricTransferOperations;

import net.minecraft.core.HolderLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/** Native Fabric lookup services for the stable API v2 ports. */
public final class FabricPlatformServices implements PlatformServices {
    private final ItemTransfer items = (level, pos, side) -> {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        var storage = ItemStorage.SIDED.find(level, pos, side);
        return storage == null ? Optional.empty() : Optional.of(new Items(new FabricItemTransactor(storage)));
    };
    private final FluidTransfer fluids = (level, pos, side) -> {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        var storage = FluidStorage.SIDED.find(level, pos, side);
        return storage == null ? Optional.empty() : Optional.of(new FluidPortImpl(storage, level.registryAccess()));
    };
    private final EnergyTransfer energy = (level, pos, side) -> {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        var storage = team.reborn.energy.api.EnergyStorage.SIDED.find(level, pos, side);
        return storage == null ? Optional.empty() : Optional.of(new Energy(storage));
    };

    @Override public Optional<ItemTransfer> itemTransfer() { return Optional.of(items); }
    @Override public Optional<FluidTransfer> fluidTransfer() { return Optional.of(fluids); }
    @Override public Optional<EnergyTransfer> energyTransfer() { return Optional.of(energy); }

    private record Items(FabricItemTransactor transactor) implements ItemPort {
        @Override
        public ItemTransferResult insert(net.minecraft.world.item.ItemStack offered, OperationMode mode) {
            return insert(offered, ItemTransferPolicy.PARTIAL, mode);
        }

        @Override
        public ItemTransferResult insert(net.minecraft.world.item.ItemStack offered, ItemTransferPolicy policy,
            OperationMode mode) {
            Objects.requireNonNull(offered, "offered");
            Objects.requireNonNull(policy, "policy");
            Objects.requireNonNull(mode, "mode");
            var remainder = transactor.insert(offered, policy != ItemTransferPolicy.PARTIAL,
                mode == OperationMode.SIMULATE);
            return ItemTransferResult.ofInsertion(offered, offered.getCount() - remainder.getCount());
        }

        @Override
        public ItemTransferResult extract(ItemMatcher matcher, int maximum, OperationMode mode) {
            return extract(matcher, 0, maximum, mode);
        }

        @Override
        public ItemTransferResult extract(ItemMatcher matcher, int minimum, int maximum, OperationMode mode) {
            Objects.requireNonNull(matcher, "matcher");
            Objects.requireNonNull(mode, "mode");
            if (minimum < 0 || maximum < minimum) {
                throw new IllegalArgumentException("Invalid extraction range");
            }
            return ItemTransferResult.ofExtraction(maximum,
                transactor.extract(matcher::matches, minimum, maximum, mode == OperationMode.SIMULATE));
        }
    }

    private record Energy(team.reborn.energy.api.EnergyStorage storage) implements ExternalEnergyPort {
        @Override
        public long insert(long offered, OperationMode mode) {
            Objects.requireNonNull(mode, "mode");
            return FabricTransferOperations.transfer(offered, mode == OperationMode.SIMULATE,
                transaction -> storage.insert(offered, transaction));
        }

        @Override
        public long extract(long requested, OperationMode mode) {
            Objects.requireNonNull(mode, "mode");
            return FabricTransferOperations.transfer(requested, mode == OperationMode.SIMULATE,
                transaction -> storage.extract(requested, transaction));
        }

        @Override public long stored() { return storage.getAmount(); }
        @Override public long capacity() { return storage.getCapacity(); }
        @Override public boolean canInsert() { return storage.supportsInsertion(); }
        @Override public boolean canExtract() { return storage.supportsExtraction(); }
    }

    private record FluidPortImpl(Storage<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant> storage,
        HolderLookup.Provider registries) implements FluidPort {
        @Override
        public FluidTransferResult insert(FluidVolume offered, OperationMode mode) {
            Objects.requireNonNull(offered, "offered");
            Objects.requireNonNull(mode, "mode");
            if (offered.isEmpty()) {
                return FluidTransferResult.nothing(offered.amount());
            }
            var resource = FabricFluidVariants.toNative(offered.requireVariant(), registries);
            long accepted = FabricTransferOperations.transferFluid(offered.amount().milliBuckets(),
                mode == OperationMode.SIMULATE, (amount, transaction) -> storage.insert(resource, amount, transaction));
            return FluidTransferResult.ofInsertion(offered, FluidAmount.of(accepted));
        }

        @Override
        public FluidTransferResult extract(FluidMatcher matcher, FluidAmount maximum, OperationMode mode) {
            Objects.requireNonNull(matcher, "matcher");
            Objects.requireNonNull(maximum, "maximum");
            Objects.requireNonNull(mode, "mode");
            if (maximum.isZero()) {
                return FluidTransferResult.nothing(maximum);
            }
            try (Transaction outer = FabricTransferOperations.openTransaction()) {
                for (var view : storage) {
                    var resource = view.getResource();
                    if (resource.isBlank() || view.getAmount() <= 0
                        || !matcher.matches(FabricFluidVariants.toApi(resource, registries),
                            FabricFluidVariants.MATCH_CONTEXT)) {
                        continue;
                    }
                    long moved = FabricTransferOperations.transferFluid(maximum.milliBuckets(), false,
                        (amount, transaction) -> storage.extract(resource, amount, transaction));
                    if (moved == 0) {
                        continue;
                    }
                    FluidVolume result = FabricFluidVariants.volume(resource, moved, registries);
                    if (mode == OperationMode.EXECUTE) {
                        outer.commit();
                    }
                    return FluidTransferResult.ofExtraction(maximum, result);
                }
            }
            return FluidTransferResult.nothing(maximum);
        }
    }
}
