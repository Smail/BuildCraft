package buildcraft.lib.platform.storage;

import java.util.Objects;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;

/** Transactional component edits for an item whose caller cannot replace its stack reference. */
final class FabricStackContext extends SingleStackStorage {
    private final ItemStack original;
    private ItemStack current;

    private FabricStackContext(ItemStack original) {
        this.original = Objects.requireNonNull(original, "original");
        current = original.copy();
    }

    static ContainerItemContext of(ItemStack original) {
        return ContainerItemContext.ofSingleSlot(new FabricStackContext(original));
    }

    @Override
    protected ItemStack getStack() {
        return current;
    }

    @Override
    protected void setStack(ItemStack stack) {
        current = Objects.requireNonNull(stack, "stack");
    }

    @Override
    protected boolean canInsert(ItemVariant variant) {
        // A raw ItemStack cannot replace its item. Containers that exchange the
        // item itself must receive an actual inventory or player-hand context.
        return variant.getItem() == original.getItem();
    }

    @Override
    protected void onFinalCommit() {
        if (current.isEmpty()) {
            original.setCount(0);
            return;
        }
        DataComponentPatch.Builder reset = DataComponentPatch.builder();
        var patch = original.getComponentsPatch().split();
        for (var type : patch.added().keySet()) {
            resetComponent(reset, type);
        }
        for (var type : patch.removed()) {
            resetComponent(reset, type);
        }
        original.applyComponents(reset.build());
        original.applyComponents(current.getComponentsPatch());
        original.setCount(current.getCount());
    }

    private <T> void resetComponent(DataComponentPatch.Builder reset, DataComponentType<T> type) {
        T prototype = original.getItem().components().get(type);
        if (prototype == null) {
            reset.remove(type);
        } else {
            reset.set(type, prototype);
        }
    }
}
