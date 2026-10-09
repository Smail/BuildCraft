package buildcraft.lib.platform.capability;

import java.util.Objects;
import javax.annotation.Nullable;
import buildcraft.lib.internal.capabilities.IBCCapabilityProvider;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Internal capability token backed by Fabric's native block lookup. */
public final class BCBlockCapability<T, C> {
    private final BlockApiLookup<T, C> lookup;
    private BCBlockCapability(Identifier id, Class<T> type, Class<C> context) {
        lookup = BlockApiLookup.get(Objects.requireNonNull(id), Objects.requireNonNull(type), context);
        lookup.registerFallback((level, pos, state, tile, side) -> tile instanceof IBCCapabilityProvider provider
            && !tile.isRemoved() ? provider.getCapability((BCBlockCapability<T, Direction>) this, (Direction) side) : null);
    }
    public static <T> BCBlockCapability<T, Direction> createSided(Identifier id, Class<T> type) {
        return new BCBlockCapability<>(id, type, Direction.class);
    }
    public Identifier name() { return lookup.getId(); }
    public BlockApiLookup<T, C> nativeLookup() { return lookup; }
    @Nullable public T getCapability(Level level, BlockPos pos, @Nullable C context) {
        return level == null || pos == null ? null : lookup.find(level, pos, context);
    }
    @Nullable public T getCapability(Level level, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity tile, @Nullable C context) {
        return level == null || pos == null ? null : lookup.find(level, pos, state, tile, context);
    }
}
