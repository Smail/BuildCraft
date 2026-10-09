package buildcraft.lib.platform.storage;

import net.minecraft.world.item.ItemStack;

/** Slot storage whose owner permits restoring exact contents during rollback. */
public interface MutableItemStorage extends ItemStorage {
    void setStackInSlot(int slot, ItemStack stack);
}
