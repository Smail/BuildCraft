package buildcraft.lib.fluid;

import java.util.Objects;
import java.util.function.Supplier;

import buildcraft.api.v2.fluid.FluidItemAdapter;
import buildcraft.api.v2.fluid.FluidVolume;
import buildcraft.lib.platform.storage.FabricTransferOperations;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/** Reads one item's native fluid storage without modifying its stack or container. */
public final class FabricFluidItemAdapter implements FluidItemAdapter {
    private final Supplier<HolderLookup.Provider> registries;

    public FabricFluidItemAdapter() {
        this(() -> RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    public FabricFluidItemAdapter(Supplier<HolderLookup.Provider> registries) {
        this.registries = Objects.requireNonNull(registries, "registries");
    }

    @Override
    public boolean supports(ItemStack stack) {
        return !fluid(stack).isEmpty();
    }

    @Override
    public FluidVolume fluid(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return FluidVolume.empty();
        }
        var storage = ContainerItemContext.withConstant(stack.copyWithCount(1)).find(FluidStorage.ITEM);
        if (storage == null) {
            return FluidVolume.empty();
        }
        try (Transaction transaction = FabricTransferOperations.openTransaction()) {
            for (var view : storage) {
                long amount = view.getAmount();
                if (amount < 0) {
                    throw new IllegalStateException("Item fluid storage returned a negative amount");
                }
                long millibuckets = amount / FabricTransferOperations.DROPLETS_PER_MILLIBUCKET;
                if (!view.isResourceBlank() && millibuckets > 0) {
                    return FabricFluidVariants.volume(view.getResource(), millibuckets,
                        Objects.requireNonNull(registries.get(), "registry provider"));
                }
            }
        }
        return FluidVolume.empty();
    }
}
