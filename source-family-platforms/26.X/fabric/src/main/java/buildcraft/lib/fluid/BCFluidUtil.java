package buildcraft.lib.fluid;

import java.util.Objects;
import java.util.Optional;
import buildcraft.lib.platform.storage.FabricFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import javax.annotation.Nullable;

import buildcraft.energy.fluid.BCFluidType;
import buildcraft.lib.internal.debug.BCLog;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Native item-fluid exchange. Simulation uses real Fabric transactions and rolls back. */
public final class BCFluidUtil {
    private BCFluidUtil() {}
    public static ItemStack getFilledBucket(BCFluidStack fluid) {
        Objects.requireNonNull(fluid);
        if (fluid.isEmpty() || fluid.getAmount() < 1000 || fluid.getFluid().getBucket() == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(fluid.getFluid().getBucket());
    }
    public static Optional<BCFluidStack> getFluidContained(ItemStack stack) {
        return getFluidHandler(stack).map(handler -> handler.drain(Integer.MAX_VALUE, BCFluidHandler.FluidAction.SIMULATE)).filter(fluid -> !fluid.isEmpty());
    }
    public static Optional<BCFluidHandlerItem> getFluidHandler(ItemStack stack) {
        Objects.requireNonNull(stack);
        if (stack.isEmpty()) return Optional.empty();
        ItemContext context = new ItemContext(stack.copy());
        Storage<FluidVariant> nativeStorage = ContainerItemContext.ofSingleSlot(context).find(FluidStorage.ITEM);
        return nativeStorage == null ? Optional.empty() : Optional.of(new ItemHandler(context, nativeStorage));
    }
    public static boolean interactWithFluidHandler(Player player, InteractionHand hand, BCFluidHandler tank) {
        return FabricFluidInteractions.interact(player, hand, LegacyFluidExport.nativeStorage(tank), Integer.MAX_VALUE).consumesAction();
    }
    /**
     * Places one bucket of {@code resource} as a source block at {@code pos}, paying for it from {@code source}.
     * Nothing is drained unless the block was actually placed.
     *
     * @return true if the fluid block was placed and the bucket volume was drained from the source
     */
    public static boolean tryPlaceFluid(@Nullable Player player, Level level, @Nullable InteractionHand hand,
        BlockPos pos, BCFluidHandler source, BCFluidStack resource) {
        if (level == null || pos == null || source == null || resource == null || resource.isEmpty()) {
            return false;
        }
        try {
            Fluid fluid = resource.getFluid();
            Fluid placed = fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
            if (placed == Fluids.EMPTY || !level.isInWorldBounds(pos)) {
                return false;
            }
            if (player != null && !level.mayInteract(player, pos)) {
                return false;
            }
            BlockState existing = level.getBlockState(pos);
            if (!existing.isAir() && !existing.canBeReplaced(placed)) {
                return false;
            }
            BlockState block = placed.defaultFluidState().createLegacyBlock();
            if (block.isAir()) {
                return false;
            }
            BCFluidStack bucket = resource.copyWithAmount(BCFluidType.BUCKET_VOLUME);
            BCFluidStack simulated = source.drain(bucket, BCFluidHandler.FluidAction.SIMULATE);
            if (simulated == null || simulated.getAmount() < BCFluidType.BUCKET_VOLUME) {
                return false;
            }
            if (!level.setBlock(pos, block, Block.UPDATE_ALL_IMMEDIATE)) {
                return false;
            }
            BCFluidStack drained = source.drain(bucket, BCFluidHandler.FluidAction.EXECUTE);
            if (drained == null || drained.getAmount() < BCFluidType.BUCKET_VOLUME) {
                // The source lied about its simulation; undo the placement so no fluid is created for free.
                level.setBlock(pos, existing, Block.UPDATE_ALL_IMMEDIATE);
                return false;
            }
            level.playSound(null, pos, placed.is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY,
                SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
            return true;
        } catch (RuntimeException e) {
            BCLog.logger.error("Failed to place fluid {} at {}", resource, pos, e);
            return false;
        }
    }
    private static final class ItemContext extends SingleStackStorage {
        private ItemStack stack;
        ItemContext(ItemStack stack) { this.stack = stack; }
        @Override protected ItemStack getStack() { return stack; }
        @Override protected void setStack(ItemStack stack) { this.stack = Objects.requireNonNull(stack); }
    }
    private record ItemHandler(ItemContext context, FabricFluidStorage storage) implements BCFluidHandlerItem {
        ItemHandler(ItemContext context, Storage<FluidVariant> storage) { this(context, new FabricFluidStorage(storage)); }
        @Override public ItemStack getContainer() { return context.stack; }
        @Override public int getTanks() { return storage.getTanks(); }
        @Override public BCFluidStack getFluidInTank(int tank) { return BCFluidStack.fromNative(storage.getFluidInTank(tank)); }
        @Override public int getTankCapacity(int tank) { return storage.getTankCapacity(tank); }
        @Override public boolean isFluidValid(int tank, BCFluidStack stack) { return storage.isFluidValid(tank, stack.toNative()); }
        @Override public int fill(BCFluidStack stack, FluidAction action) { return storage.fill(stack.toNative(), action.simulate()); }
        @Override public BCFluidStack drain(BCFluidStack stack, FluidAction action) { return BCFluidStack.fromNative(storage.drain(stack.toNative(), action.simulate())); }
        @Override public BCFluidStack drain(int maximum, FluidAction action) { return BCFluidStack.fromNative(storage.drain(maximum, action.simulate())); }
    }
}
