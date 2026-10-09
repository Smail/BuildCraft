package buildcraft.lib.fluid;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import buildcraft.lib.platform.storage.FabricFluidStorage;
import buildcraft.lib.platform.storage.FabricTransferOperations;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/** Composite native view retaining each tank's journal, slot identity and input/output priority. */
public final class FabricTankManager implements SlottedStorage<FluidVariant> {
    private final List<FabricTank> tanks;
    private final FabricFluidStorage millibuckets;

    public FabricTankManager(List<FabricTank> tanks) {
        this.tanks = List.copyOf(Objects.requireNonNull(tanks, "tanks"));
        var names = new HashSet<String>();
        var identities = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<FabricTank, Boolean>());
        for (FabricTank tank : this.tanks) {
            if (!names.add(tank.getTankName()) || !identities.add(tank)) {
                throw new IllegalArgumentException("Duplicate tank name or backing storage");
            }
        }
        millibuckets = new FabricFluidStorage(this);
    }

    public FabricFluidStorage millibuckets() { return millibuckets; }
    public List<FabricTank> tanks() { return tanks; }
    @Override public int getSlotCount() { return tanks.size(); }

    @Override
    public SingleSlotStorage<FluidVariant> getSlot(int slot) {
        return tanks.get(Objects.checkIndex(slot, tanks.size())).nativeStorage().getSlot(0);
    }

    @Override
    public Iterator<StorageView<FluidVariant>> iterator() {
        return IntStream.range(0, tanks.size()).<StorageView<FluidVariant>>mapToObj(this::getSlot).iterator();
    }

    @Override
    public long insert(FluidVariant resource, long maximum, TransactionContext parent) {
        return transfer(resource, maximum, parent, true);
    }

    @Override
    public long extract(FluidVariant resource, long maximum, TransactionContext parent) {
        return transfer(resource, maximum, parent, false);
    }

    private long transfer(FluidVariant resource, long maximum, TransactionContext parent, boolean inserting) {
        StoragePreconditions.notBlankNotNegative(resource, maximum);
        Objects.requireNonNull(parent, "transaction");
        try (Transaction operation = parent.openNested()) {
            long moved = 0;
            // Dedicated inputs/outputs precede bidirectional tanks, preserving the existing manager policy.
            for (int pass = 0; pass < 2 && moved < maximum; pass++) {
                for (FabricTank tank : tanks) {
                    boolean enabled = inserting ? tank.canFill() : tank.canDrain();
                    boolean dedicated = inserting ? !tank.canDrain() : !tank.canFill();
                    if (!enabled || dedicated != (pass == 0)) continue;
                    long remaining = maximum - moved;
                    long transferred = inserting ? tank.nativeStorage().insert(resource, remaining, operation)
                        : tank.nativeStorage().extract(resource, remaining, operation);
                    FabricTransferOperations.checkTransfer(transferred, remaining);
                    moved += transferred;
                    if (moved == maximum) break;
                }
            }
            operation.commit();
            return moved;
        }
    }

    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        Objects.requireNonNull(registries, "registries");
        CompoundTag result = new CompoundTag();
        for (FabricTank tank : tanks) {
            result.put(tank.getTankName(), tank.serializeNBT(registries));
        }
        return result;
    }

    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        Objects.requireNonNull(registries, "registries");
        Objects.requireNonNull(tag, "tag");
        try (Transaction operation = FabricTransferOperations.openTransaction()) {
            for (FabricTank tank : tanks) {
                if (tag.contains(tank.getTankName()) && tag.getCompound(tank.getTankName()).isEmpty()) {
                    throw new IllegalArgumentException("Saved tank data is not a compound: " + tank.getTankName());
                }
                tank.deserializeNBT(registries, tag.getCompound(tank.getTankName()).orElseGet(CompoundTag::new));
            }
            operation.commit();
        }
    }
}
