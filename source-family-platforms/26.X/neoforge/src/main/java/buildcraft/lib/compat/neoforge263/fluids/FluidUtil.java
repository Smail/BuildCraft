//? source if >=26.3
/*
 * Copyright (c) 2026 the BuildCraft Community Edition contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.neoforge263.fluids;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import buildcraft.lib.compat.neoforge263.fluids.capability.IFluidHandler;
import buildcraft.lib.compat.neoforge263.fluids.capability.IFluidHandler.FluidAction;
import buildcraft.lib.compat.neoforge263.fluids.capability.IFluidHandlerItem;
import buildcraft.lib.compat.transfer.TransferInterop;
import buildcraft.lib.compat.transfer.TransferJournal;

/**
 * The subset of NeoForge's removed legacy fluid helpers that BuildCraft uses, rebuilt on the 26.3 transfer API.
 * Item containers are reached through {@link ItemAccess}; world placement uses NeoForge's native placement rules.
 */
public final class FluidUtil {
    private FluidUtil() {}

    /**
     * Returns a fluid handler for an item container. Fills and drains mutate {@code stack} where the item stays the
     * same; if the item changes (e.g. a bucket being emptied) the new item is reported by
     * {@link IFluidHandlerItem#getContainer()}.
     */
    public static Optional<IFluidHandlerItem> getFluidHandler(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        SimpleContainer container = new SimpleContainer(stack) {
            // Do not clamp oversized stacks to their max size, the caller owns the original count.
            @Override
            public void setItem(int slot, ItemStack item, boolean performSideEffects) {
                getItems().set(slot, item);
            }
        };
        ItemAccess access = ItemAccess.forHandlerIndex(VanillaContainerWrapper.of(container), 0);
        ResourceHandler<FluidResource> handler = access.getCapability(Capabilities.Fluid.ITEM);
        if (handler == null) {
            return Optional.empty();
        }
        return Optional.of(new ItemContainerFluidHandler(TransferInterop.importFluids(handler), container));
    }

    /** Returns the fluid that could be drained from one item of {@code container}, if any. */
    public static Optional<FluidStack> getFluidContained(ItemStack container) {
        if (container == null || container.isEmpty()) {
            return Optional.empty();
        }
        return getFluidHandler(container.copyWithCount(1))
            .map(handler -> handler.drain(Integer.MAX_VALUE, FluidAction.SIMULATE))
            .filter(fluid -> !fluid.isEmpty());
    }

    /**
     * Places one bucket of {@code resource} at {@code pos}, draining it from {@code source} on success.
     *
     * @param hand unused since 26.3, kept so callers stay identical across targets
     * @return true if the fluid was placed (or vaporized) and drained from the source
     */
    public static boolean tryPlaceFluid(@Nullable Player player, Level level, @Nullable InteractionHand hand, BlockPos pos,
        IFluidHandler source, FluidStack resource) {
        if (level == null || pos == null || source == null || resource == null || resource.isEmpty()
            || resource.getAmount() < FluidType.BUCKET_VOLUME) {
            return false;
        }
        FluidStack bucket = resource.copyWithAmount(FluidType.BUCKET_VOLUME);
        if (source.drain(bucket, FluidAction.SIMULATE).getAmount() < FluidType.BUCKET_VOLUME) {
            return false;
        }
        // Legacy LiquidBlockContainer placement ignored the placeLiquid result, so validatePlaced stays false.
        if (!net.neoforged.neoforge.transfer.fluid.FluidUtil.tryPlaceFluid(FluidResource.of(resource), player, level, pos, false)) {
            return false;
        }
        source.drain(bucket, FluidAction.EXECUTE);
        return true;
    }

    /**
     * Handles a player right-clicking a fluid handler with a held container: first tries to fill the container from
     * the handler, then to empty the container into it. The held item is updated and extra containers are stowed
     * by the native player-interaction {@link ItemAccess}.
     *
     * @return true if fluid was moved
     */
    public static boolean interactWithFluidHandler(Player player, InteractionHand hand, IFluidHandler handler) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(hand, "hand");
        Objects.requireNonNull(handler, "handler");
        if (player.getItemInHand(hand).isEmpty()) {
            return false;
        }
        ResourceHandler<FluidResource> held = ItemAccess.forPlayerInteraction(player, hand).oneByOne()
            .getCapability(Capabilities.Fluid.ITEM);
        if (held == null) {
            return false;
        }
        return fillHeldContainer(player, held, handler) || emptyHeldContainer(player, held, handler);
    }

    private static boolean fillHeldContainer(Player player, ResourceHandler<FluidResource> held, IFluidHandler source) {
        FluidStack available = source.drain(Integer.MAX_VALUE, FluidAction.SIMULATE);
        if (available.isEmpty()) {
            return false;
        }
        FluidResource resource = FluidResource.of(available);
        try (Transaction transaction = Transaction.open(TransferJournal.current())) {
            int accepted = held.insert(resource, available.getAmount(), transaction);
            FluidStack moved = available.copyWithAmount(Math.max(0, accepted));
            if (accepted <= 0 || source.drain(moved, FluidAction.SIMULATE).getAmount() != accepted) {
                return false;
            }
            transaction.commit();
            source.drain(moved, FluidAction.EXECUTE);
        }
        triggerSound(player, resource, true);
        return true;
    }

    private static boolean emptyHeldContainer(Player player, ResourceHandler<FluidResource> held, IFluidHandler destination) {
        for (int index = 0; index < held.size(); index++) {
            FluidResource resource = held.getResource(index);
            if (resource.isEmpty()) {
                continue;
            }
            int fillable = destination.fill(resource.toStack(held.getAmountAsInt(index)), FluidAction.SIMULATE);
            if (fillable <= 0) {
                continue;
            }
            try (Transaction transaction = Transaction.open(TransferJournal.current())) {
                int extracted = held.extract(index, resource, fillable, transaction);
                if (extracted <= 0 || destination.fill(resource.toStack(extracted), FluidAction.SIMULATE) != extracted) {
                    continue;
                }
                transaction.commit();
                destination.fill(resource.toStack(extracted), FluidAction.EXECUTE);
            }
            triggerSound(player, resource, false);
            return true;
        }
        return false;
    }

    private static void triggerSound(Player player, FluidResource resource, boolean pickup) {
        Vec3 position = new Vec3(player.getX(), player.getY() + 0.5, player.getZ());
        net.neoforged.neoforge.transfer.fluid.FluidUtil.triggerSoundAndGameEvent(resource, player.level(), position, player, pickup);
    }

    /** Returns a filled bucket for the fluid, or an empty stack if no bucket can hold it. The amount is ignored. */
    public static ItemStack getFilledBucket(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (fluidStack.getComponents().isEmpty()) {
            if (fluidStack.is(Fluids.WATER)) {
                return new ItemStack(Items.WATER_BUCKET);
            }
            if (fluidStack.is(Fluids.LAVA)) {
                return new ItemStack(Items.LAVA_BUCKET);
            }
        }
        return fluidStack.getFluidType().getBucket(fluidStack);
    }

    private record ItemContainerFluidHandler(IFluidHandler delegate, SimpleContainer container) implements IFluidHandlerItem {
        @Override public ItemStack getContainer() { return container.getItem(0); }
        @Override public int getTanks() { return delegate.getTanks(); }
        @Override public FluidStack getFluidInTank(int tank) { return delegate.getFluidInTank(tank); }
        @Override public int getTankCapacity(int tank) { return delegate.getTankCapacity(tank); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return delegate.isFluidValid(tank, stack); }
        @Override public int fill(FluidStack resource, FluidAction action) { return delegate.fill(resource, action); }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return delegate.drain(resource, action); }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return delegate.drain(maxDrain, action); }
    }
}
