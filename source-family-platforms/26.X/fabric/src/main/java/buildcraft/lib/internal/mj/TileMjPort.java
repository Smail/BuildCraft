package buildcraft.lib.internal.mj;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.energy.MjAmount;
import buildcraft.api.v2.energy.MjPort;
import buildcraft.api.v2.energy.MjPortDescriptor;
import buildcraft.api.v2.energy.MjPortRole;
import buildcraft.api.v2.energy.MjTransferResult;
import buildcraft.lib.internal.capabilities.IBCCapabilityProvider;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * Native {@link MjPort} view of one side of a BuildCraft block entity. The entity's MJ interfaces
 * (connector, receiver, redstone receiver, readable, passive provider) stay the single source of truth;
 * this class only adapts them to the loader-neutral port contract that Fabric publishes through
 * {@link FabricMjLookups}.
 */
public final class TileMjPort implements MjPort {
    private static final Identifier NETWORK_ID = Identifier.fromNamespaceAndPath("buildcraft", "mj");
    private static final MjAmount UNKNOWN_RATE = MjAmount.ofMicro(Long.MAX_VALUE);

    @Nullable private final IMjConnector connector;
    @Nullable private final IMjReceiver receiver;
    @Nullable private final IMjRedstoneReceiver redstone;
    @Nullable private final IMjReadable readable;
    @Nullable private final IMjPassiveProvider provider;

    private TileMjPort(@Nullable IMjConnector connector, @Nullable IMjReceiver receiver,
                       @Nullable IMjRedstoneReceiver redstone, @Nullable IMjReadable readable,
                       @Nullable IMjPassiveProvider provider) {
        this.connector = connector;
        this.receiver = receiver;
        this.redstone = redstone;
        this.readable = readable;
        this.provider = provider;
    }

    /** @return the port for this side, or empty when the entity exposes no MJ interface there. */
    public static Optional<TileMjPort> of(IBCCapabilityProvider entity, @Nullable Direction side) {
        Objects.requireNonNull(entity, "entity");
        IMjConnector connector = entity.getCapability(MjCapabilities.CAP_CONNECTOR, side);
        IMjReceiver receiver = entity.getCapability(MjCapabilities.CAP_RECEIVER, side);
        IMjRedstoneReceiver redstone = entity.getCapability(MjCapabilities.CAP_REDSTONE_RECEIVER, side);
        IMjReadable readable = entity.getCapability(MjCapabilities.CAP_READABLE, side);
        IMjPassiveProvider provider = entity.getCapability(MjCapabilities.CAP_PASSIVE_PROVIDER, side);
        if (connector == null && receiver == null && redstone == null && readable == null && provider == null) {
            return Optional.empty();
        }
        return Optional.of(new TileMjPort(connector, receiver, redstone, readable, provider));
    }

    @Override public MjTransferResult insert(MjAmount offered, OperationMode mode) {
        Objects.requireNonNull(offered, "offered");
        Objects.requireNonNull(mode, "mode");
        if (receiver == null || !receiver.canReceive() || offered.isZero()) return MjTransferResult.none(offered);
        long remainder = receiver.receivePower(offered.microMj(),
            mode == OperationMode.EXECUTE ? FluidAction.EXECUTE : FluidAction.SIMULATE);
        remainder = Math.max(0L, Math.min(offered.microMj(), remainder));
        return MjTransferResult.of(offered, MjAmount.ofMicro(offered.microMj() - remainder));
    }

    @Override public MjTransferResult extract(MjAmount requested, OperationMode mode) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(mode, "mode");
        if (provider == null || requested.isZero()) return MjTransferResult.none(requested);
        long moved = provider.extractPower(0L, requested.microMj(), mode == OperationMode.EXECUTE);
        moved = Math.max(0L, Math.min(requested.microMj(), moved));
        return MjTransferResult.of(requested, MjAmount.ofMicro(moved));
    }

    @Override public MjAmount stored() {
        return MjAmount.ofMicro(readable == null ? 0L : Math.max(0L, readable.getStored()));
    }

    @Override public MjAmount capacity() {
        return MjAmount.ofMicro(readable == null ? 0L : Math.max(0L, readable.getCapacity()));
    }

    @Override public boolean canInsert() { return receiver != null && receiver.canReceive(); }

    @Override public boolean canExtract() { return provider != null; }

    /** Structural metadata only: legacy power queries are live operations and must not run during discovery. */
    public MjPortDescriptor descriptor() {
        EnumSet<MjPortRole> roles = EnumSet.noneOf(MjPortRole.class);
        if (connector != null) roles.add(MjPortRole.CONNECTOR);
        if (receiver != null) roles.add(MjPortRole.CONSUMER);
        if (redstone != null) roles.add(MjPortRole.REDSTONE_RECEIVER);
        if (readable != null) roles.add(MjPortRole.READABLE);
        if (provider != null) roles.add(MjPortRole.PASSIVE_PROVIDER);
        return new MjPortDescriptor(NETWORK_ID, roles, receiver == null ? MjAmount.ZERO : UNKNOWN_RATE,
            provider == null ? MjAmount.ZERO : UNKNOWN_RATE);
    }

    /** @return the legacy connector for this side, used by connection rules. */
    @Nullable public IMjConnector connector() { return connector; }
}
