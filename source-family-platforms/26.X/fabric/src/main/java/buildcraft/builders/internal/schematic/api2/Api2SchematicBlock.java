package buildcraft.builders.internal.schematic.api2;

import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.permission.AutomationActor;
import buildcraft.api.v2.schematic.SchematicAdapter;
import buildcraft.api.v2.schematic.SchematicPlacementContext;
import buildcraft.api.v2.schematic.SchematicResult;
import buildcraft.api.v2.schematic.SnapshotElement;
import buildcraft.builders.internal.schematic.legacy.ISchematicBlock;
import buildcraft.builders.internal.schematic.legacy.SchematicBlockContext;
import buildcraft.lib.fluid.FuelApiBridge;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import buildcraft.lib.fluid.BCFluidStack;
import buildcraft.lib.compat.GameProfileCompat;

/** Internal compatibility shell allowing API2 snapshot elements to run in the existing Builder scheduler. */
public final class Api2SchematicBlock implements ISchematicBlock {
    private final SnapshotElement element;
    private final SchematicAdapter adapter;

    public Api2SchematicBlock(SnapshotElement element, SchematicAdapter adapter) {
        this.element = element;
        this.adapter = adapter;
    }

    public SnapshotElement element() { return element; }
    public SchematicAdapter adapter() { return adapter; }

    public Identifier typeId() { return element.typeId(); }
    public void init(SchematicBlockContext context) { }
    public boolean isAir() { return adapter.isAir(element); }
    public Set<BlockPos> getRequiredBlockOffsets() { return adapter.requiredBlockOffsets(element); }
    public List<ItemStack> computeRequiredItems(Level level) { return adapter.requirements(element, level).items(); }
    public List<ItemStack> computeRequiredItemsForPlacement(Level level) { return adapter.placementRequirements(element, level).items(); }
    public List<ItemStack> computeDeferredRequiredItems(Level level) { return adapter.deferredItems(element, level); }
    public List<ItemStack> computeMissingDeferredRequiredItems(Level level, BlockPos blockPos) {
        return adapter.missingDeferredItems(element, level, blockPos);
    }
    public ItemStack insertDeferredItem(Level level, BlockPos blockPos, ItemStack stack, boolean simulate) {
        return adapter.insertDeferredItem(element, level, blockPos, stack, simulate ? OperationMode.SIMULATE : OperationMode.EXECUTE);
    }
    public List<BCFluidStack> computeRequiredFluids(Level level) {
        return adapter.requiredFluids(element, level).stream().map(FuelApiBridge::stackOf).filter(stack -> !stack.isEmpty()).collect(Collectors.toList());
    }
    public ISchematicBlock getRotated(Rotation rotation) {
        SnapshotElement rotated = adapter.rotate(element, rotation);
        return new Api2SchematicBlock(rotated, adapter);
    }
    public boolean canBuild(Level world, BlockPos blockPos) {
        return adapter.canPlace(element, context(world, blockPos, null, OperationMode.SIMULATE));
    }
    public boolean isReadyToBuild(Level world, BlockPos blockPos) {
        return adapter.readyToPlace(element, context(world, blockPos, null, OperationMode.SIMULATE));
    }
    public boolean build(Level world, BlockPos blockPos) { return build(world, blockPos, null); }
    public boolean build(Level world, BlockPos blockPos, @Nullable Player actor) {
        return success(adapter.place(element, context(world, blockPos, actor, OperationMode.EXECUTE)));
    }
    public boolean buildWithoutChecks(Level world, BlockPos blockPos) {
        return success(adapter.placeUnchecked(element, context(world, blockPos, null, OperationMode.EXECUTE)));
    }
    public boolean isBuilt(Level world, BlockPos blockPos) { return adapter.isPlaced(element, world, blockPos); }
    public CompoundTag serializeNBT() { return Api2SnapshotPersistence.write(element); }
    public void deserializeNBT(CompoundTag nbt) { throw new UnsupportedOperationException("API2 snapshot wrappers are immutable"); }

    private SchematicPlacementContext context(Level level, BlockPos pos, @Nullable Player player, OperationMode mode) {
        AutomationActor actor = player == null
            ? AutomationActor.unknown()
            : AutomationActor.player(player.getUUID(), GameProfileCompat.name(player.getGameProfile()));
        return new SchematicPlacementContext(level, pos, actor, mode);
    }

    private static boolean success(SchematicResult result) { return result.status() == SchematicResult.Status.SUCCESS; }
}

