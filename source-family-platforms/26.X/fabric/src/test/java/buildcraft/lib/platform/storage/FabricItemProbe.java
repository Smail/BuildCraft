package buildcraft.lib.platform.storage;

import buildcraft.lib.compat.transfer.TransferJournal;
import buildcraft.lib.inventory.FabricItemTransactor;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/** Exercises real native sided containers and BuildCraft exports under the Fabric loader. */
public final class FabricItemProbe {
    private FabricItemProbe() {}

    public static void main(String[] args) {
        SidedContainer inventory = new SidedContainer();
        var east = ContainerStorage.of(inventory, Direction.EAST);
        var west = ContainerStorage.of(inventory, Direction.WEST);
        var nativeItem = ItemVariant.of(Items.STONE);
        equal(1, east.getSlotCount());
        equal(1, west.getSlotCount());
        try (Transaction outer = Transaction.openOuter()) {
            equal(4, east.insert(nativeItem, 4, outer));
            equal(7, west.insert(nativeItem, 7, outer));
            equal(4, inventory.getItem(0).getCount());
            equal(7, inventory.getItem(1).getCount());
        }
        equal(0, inventory.getItem(0).getCount());
        equal(0, inventory.getItem(1).getCount());
        try (Transaction outer = Transaction.openOuter()) {
            equal(0, east.extract(nativeItem, 1, outer));
            equal(4, east.insert(nativeItem, 4, outer));
            outer.commit();
        }
        equal(4, inventory.getItem(0).getCount());
        var imported = StorageAdapters.fromNativeItems(east);
        check(StorageAdapters.toNativeItems(imported, null) == east);
        inventory.clearContent();
        var bulk = NonNullList.<ItemStack>create();
        bulk.add(new ItemStack(Items.STONE, 40));
        bulk.add(new ItemStack(Items.STONE, 40));
        var left = new FabricItemTransactor(east).insert(bulk, true);
        equal(1, left.size());
        equal(16, left.getFirst().getCount());
        equal(0, inventory.getItem(0).getCount());

        Backing backing = new Backing();
        var journal = new TransferJournal<>(backing::snapshot, backing::restore, before -> {});
        var exported = StorageAdapters.toNativeItems(backing, journal);
        check(exported.getSlot(0) == exported.getSlot(0));
        check(StorageAdapters.fromNativeItems(exported) == backing);
        try (Transaction outer = Transaction.openOuter()) {
            try {
                exported.insert(nativeItem, 80, outer);
                throw new AssertionError("Expected second-slot failure");
            } catch (IllegalStateException expected) {
                equal(0, backing.stacks[0].getCount());
                equal(0, backing.stacks[1].getCount());
            }
            outer.commit();
        }
        equal(0, backing.stacks[0].getCount());
        equal(0, backing.stacks[1].getCount());
        System.out.println("Fabric item sided access, bulk simulation, identity and export recovery probes passed");
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Item storage invariant failed");
    }

    private static void equal(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static final class SidedContainer extends SimpleContainer implements WorldlyContainer {
        SidedContainer() { super(2); }
        @Override public int[] getSlotsForFace(Direction direction) { return new int[] { direction == Direction.EAST ? 0 : 1 }; }
        @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) { return true; }
        @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) { return direction == Direction.WEST; }
    }

    private static final class Backing implements ItemStorage {
        ItemStack[] stacks = {ItemStack.EMPTY, ItemStack.EMPTY};

        ItemStack[] snapshot() { return new ItemStack[] {stacks[0].copy(), stacks[1].copy()}; }
        void restore(ItemStack[] state) { stacks = new ItemStack[] {state[0].copy(), state[1].copy()}; }
        @Override public int getSlots() { return stacks.length; }
        @Override public ItemStack getStackInSlot(int slot) { return stacks[slot]; }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }

        @Override public ItemStack insertItem(int slot, ItemStack offered, boolean simulate) {
            int accepted = Math.min(offered.getCount(), 64 - stacks[slot].getCount());
            if (!simulate) stacks[slot] = offered.copyWithCount(stacks[slot].getCount() + accepted);
            if (slot == 1) throw new IllegalStateException("Backing item slot failed after mutation");
            return offered.copyWithCount(offered.getCount() - accepted);
        }

        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int extracted = Math.min(amount, stacks[slot].getCount());
            ItemStack result = stacks[slot].copyWithCount(extracted);
            if (!simulate) stacks[slot] = stacks[slot].copyWithCount(stacks[slot].getCount() - extracted);
            return result;
        }
    }
}
