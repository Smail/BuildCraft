package buildcraft.lib.gui.slot;

import buildcraft.lib.platform.storage.ItemStorage;
import net.minecraft.world.item.ItemStack;

/** Gives vanilla a mutable private copy and writes changes through the owner's slot setter. */
public class ItemStorageCopySlot extends SlotItemStorage {
    private ItemStack view;
    private ItemStack original;
    public ItemStorageCopySlot(ItemStorage handler, int slot, int x, int y) { super(handler, slot, x, y); }
    public ItemStorageCopySlot(SlotItemStorage slot) { this(slot.handler, slot.index, slot.x, slot.y); }
    @Override public ItemStack getItem() {
        if (view == null || ItemStack.matches(view, original)) {
            original = handler.getStackInSlot(index).copy(); view = original.copy();
        }
        return view;
    }
    @Override public void set(ItemStack stack) { view = null; original = null; super.set(stack.copy()); }
    @Override public void setChanged() {
        if (view != null && !ItemStack.matches(view, original)) {
            ItemStack modified = view; view = null; original = null; super.set(modified.copy());
        } else super.setChanged();
    }
    @Override public ItemStack remove(int maximum) { setChanged(); view = null; return super.remove(maximum); }
}
