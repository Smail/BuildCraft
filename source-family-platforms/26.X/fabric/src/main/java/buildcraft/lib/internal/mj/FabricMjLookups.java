package buildcraft.lib.internal.mj;

import java.util.Objects;

import buildcraft.api.v2.energy.MjConnectionRule;
import buildcraft.api.v2.energy.MjPort;
import buildcraft.api.v2.energy.MjPortDescriptor;
import buildcraft.api.v2.energy.MjPortProvider;

import buildcraft.lib.internal.capabilities.IBCCapabilityProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;

/** Sided Fabric publication of the loader-neutral MJ endpoint and its structural metadata. */
public final class FabricMjLookups {
    public static final BlockApiLookup<MjPort, Direction> PORT = lookup("mj_port", MjPort.class);
    public static final BlockApiLookup<MjPortDescriptor, Direction> DESCRIPTOR = lookup("mj_descriptor", MjPortDescriptor.class);
    public static final BlockApiLookup<MjConnectionRule, Direction> CONNECTION_RULE =
        lookup("mj_connection_rule", MjConnectionRule.class);
    private static boolean installed;

    private FabricMjLookups() {}

    private static <T> BlockApiLookup<T, Direction> lookup(String name, Class<T> type) {
        return BlockApiLookup.get(Identifier.fromNamespaceAndPath("buildcraftlib", name), type, Direction.class);
    }

    public static synchronized void install() {
        if (installed) return;
        PORT.registerFallback((level, pos, state, entity, side) -> entity instanceof MjPortProvider provider
            && !entity.isRemoved() ? Objects.requireNonNull(provider.mjPort(side), "MJ port optional").orElse(null) : null);
        DESCRIPTOR.registerFallback((level, pos, state, entity, side) -> entity instanceof MjPortProvider provider
            && !entity.isRemoved() ? Objects.requireNonNull(provider.mjPortDescriptor(side), "MJ descriptor optional")
                .orElse(null) : null);
        // BuildCraft block entities decide connectivity through their connector, which sees the neighbour's connector.
        CONNECTION_RULE.registerFallback((level, pos, state, entity, side) -> {
            if (!(entity instanceof IBCCapabilityProvider provider) || entity.isRemoved()) return null;
            IMjConnector connector = provider.getCapability(MjCapabilities.CAP_CONNECTOR, side);
            if (connector == null) return null;
            return context -> {
                Objects.requireNonNull(context, "context");
                BlockPos otherPos = context.position().relative(context.side());
                BlockEntity other = context.level().getBlockEntity(otherPos);
                if (!(other instanceof IBCCapabilityProvider otherProvider)) return true;
                IMjConnector otherConnector = otherProvider.getCapability(MjCapabilities.CAP_CONNECTOR,
                    context.side().getOpposite());
                return otherConnector == null || connector.canConnect(otherConnector);
            };
        });
        installed = true;
    }
}
