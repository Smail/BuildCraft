package buildcraft.lib.gui.slot;

import java.util.Objects;
import buildcraft.lib.platform.storage.ItemStorage;
import buildcraft.lib.platform.storage.MutableItemStorage;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Vanilla menu slot backed by the existing internal slot contract. */
public class SlotItemStorage extends Slot {
    protected final ItemStorage handler;
    protected final int index;
    public SlotItemStorage(ItemStorage handler, int index, int x, int y) {
        super(new SimpleContainer(0), index, x, y);
        this.handler = Objects.requireNonNull(handler); this.index = Objects.checkIndex(index, handler.getSlots());
    }
    @Override public boolean mayPlace(ItemStack stack) { return !stack.isEmpty() && handler.isItemValid(index, stack); }
    @Override public ItemStack getItem() { return handler.getStackInSlot(index); }
    @Override public void set(ItemStack stack) {
        if (!(handler instanceof MutableItemStorage mutable)) throw new IllegalStateException("Cannot set a read-only storage slot");
        mutable.setStackInSlot(index, Objects.requireNonNull(stack)); setChanged();
    }
    public void initialize(ItemStack stack) { set(stack); }
    @Override public int getMaxStackSize() { return handler.getSlotLimit(index); }
    @Override public int getMaxStackSize(ItemStack stack) { return Math.min(stack.getMaxStackSize(), getMaxStackSize()); }
    @Override public boolean mayPickup(Player player) { return !handler.extractItem(index, 1, true).isEmpty(); }
    @Override public ItemStack remove(int maximum) { return handler.extractItem(index, maximum, false); }
    public ItemStorage getItemHandler() { return handler; }
    @Override public boolean isSameInventory(Slot other) { return other instanceof SlotItemStorage slot && slot.handler == handler; }
}
