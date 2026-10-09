package buildcraft.transport.stripes;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Retains complete block data while a Stripes operation changes two positions. */
final class PipeBlockSnapshot {
    private final Level level;
    private final BlockPos position;
    private final BlockState state;
    private final CompoundTag data;

    private PipeBlockSnapshot(Level level, BlockPos position) {
        this.level = Objects.requireNonNull(level, "level");
        this.position = Objects.requireNonNull(position, "position").immutable();
        state = level.getBlockState(position);
        BlockEntity entity = level.getBlockEntity(position);
        data = entity == null ? null : entity.saveWithFullMetadata(level.registryAccess());
    }

    static PipeBlockSnapshot create(ResourceKey<Level> dimension, Level level, BlockPos position) {
        if (!Objects.requireNonNull(dimension, "dimension").equals(level.dimension())) {
            throw new IllegalArgumentException("Snapshot dimension differs from its level");
        }
        return new PipeBlockSnapshot(level, position);
    }

    BlockState getState() { return state; }

    void restore() {
        if (level.getBlockState(position) != state && !level.setBlock(position, state, 3)) {
            throw new IllegalStateException("Cannot restore Stripes block at " + position);
        }
        if (data != null) {
            BlockEntity entity = BlockEntity.loadStatic(position, state, data.copy(), level.registryAccess());
            if (entity == null) throw new IllegalStateException("Cannot restore Stripes block entity at " + position);
            level.setBlockEntity(entity);
        }
    }
}
