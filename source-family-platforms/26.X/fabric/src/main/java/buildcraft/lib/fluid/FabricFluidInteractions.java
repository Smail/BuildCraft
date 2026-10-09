package buildcraft.lib.fluid;

import java.util.Objects;
import java.util.function.Predicate;

import buildcraft.lib.platform.storage.FabricTransferOperations;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;

/** Transactional bucket/container exchange, including the native creative inventory policy. */
public final class FabricFluidInteractions {
    private FabricFluidInteractions() {}

    public static long move(Storage<FluidVariant> source, Storage<FluidVariant> destination,
        Predicate<FluidVariant> filter, long maximumMillibuckets, boolean simulate) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(filter, "filter");
        if (source == destination) {
            if (maximumMillibuckets < 0) throw new IllegalArgumentException("Negative fluid transfer amount");
            return 0;
        }
        return FabricTransferOperations.transferFluid(maximumMillibuckets, simulate,
            (amount, transaction) -> StorageUtil.move(source, destination, filter, amount, transaction));
    }

    public static InteractionResult interact(Player player, InteractionHand hand, Storage<FluidVariant> tank,
        long maximumMillibuckets) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(hand, "hand");
        Objects.requireNonNull(tank, "tank");
        if (maximumMillibuckets < 0) throw new IllegalArgumentException("Negative fluid transfer amount");
        if (player.isSpectator() || player.level().isClientSide()) return InteractionResult.PASS;
        var container = ContainerItemContext.forPlayerInteraction(player, hand).find(FluidStorage.ITEM);
        if (container == null) return InteractionResult.PASS;
        long moved = move(container, tank, variant -> true, maximumMillibuckets, false);
        if (moved == 0) {
            moved = move(tank, container, variant -> true, maximumMillibuckets, false);
        }
        return moved == 0 ? InteractionResult.PASS : InteractionResult.SUCCESS;
    }
}
