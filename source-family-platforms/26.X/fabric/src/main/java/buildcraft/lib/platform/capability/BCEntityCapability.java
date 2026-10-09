package buildcraft.lib.platform.capability;

import javax.annotation.Nullable;
import net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

public final class BCEntityCapability<T, C> {
    private final EntityApiLookup<T, C> lookup;
    private BCEntityCapability(Identifier id, Class<T> type, Class<C> context) { lookup = EntityApiLookup.get(id, type, context); }
    public static <T> BCEntityCapability<T, Direction> createSided(Identifier id, Class<T> type) { return new BCEntityCapability<>(id, type, Direction.class); }
    @Nullable public T getCapability(@Nullable Entity entity, @Nullable C side) { return entity == null || entity.isRemoved() ? null : lookup.find(entity, side); }
    public EntityApiLookup<T, C> nativeLookup() { return lookup; }
}
