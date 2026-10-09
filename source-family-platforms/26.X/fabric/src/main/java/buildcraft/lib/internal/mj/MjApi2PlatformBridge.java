package buildcraft.lib.internal.mj;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.energy.*;
import buildcraft.lib.internal.api.v2.energy.MjRuntimeLookup;
import buildcraft.lib.platform.storage.FabricTransferOperations;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import team.reborn.energy.api.EnergyStorage;

/** Native MJ lookup and the existing configured whole-FE conversion boundary. */
public final class MjApi2PlatformBridge {
    private static final Identifier NETWORK_ID = Identifier.fromNamespaceAndPath("buildcraft", "mj");
    private static final MjAmount UNKNOWN_RATE = MjAmount.ofMicro(Long.MAX_VALUE);
    private static final Backend BACKEND = new Backend();

    private MjApi2PlatformBridge() {}

    public static void install() {
        FabricMjLookups.install();
        MjRuntimeLookup.install(BACKEND);
    }

    private static final class Backend implements MjRuntimeLookup.Backend {
        @Override
        public Optional<MjPort> port(Level level, BlockPos pos, Direction side) {
            MjPort nativePort = find(FabricMjLookups.PORT, level, pos, side);
            return nativePort != null ? Optional.of(nativePort) : Optional.ofNullable(external(level, pos, side));
        }

        @Override
        public Optional<MjPortDescriptor> descriptor(Level level, BlockPos pos, Direction side) {
            MjPortDescriptor descriptor = find(FabricMjLookups.DESCRIPTOR, level, pos, side);
            if (descriptor != null) return Optional.of(descriptor);
            MjPort port = find(FabricMjLookups.PORT, level, pos, side);
            if (port != null) return Optional.of(describe(port));
            ExternalPort external = external(level, pos, side);
            return external == null ? Optional.empty() : Optional.of(describe(external));
        }

        @Override
        public boolean canConnect(MjConnectionContext context) {
            Objects.requireNonNull(context, "context");
            MjConnectionRule local = find(FabricMjLookups.CONNECTION_RULE, context.level(), context.position(), context.side());
            if (local != null && !local.canConnect(context)) return false;
            BlockPos remotePosition = context.position().relative(context.side());
            MjConnectionRule remote = find(FabricMjLookups.CONNECTION_RULE, context.level(), remotePosition,
                context.side().getOpposite());
            return remote == null || remote.canConnect(new MjConnectionContext(context.level(), remotePosition,
                context.side().getOpposite(), context.remote(), context.local()));
        }
    }

    private static <T> T find(BlockApiLookup<T, Direction> lookup, Level level, BlockPos pos, Direction side) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        return lookup.find(level, pos, side);
    }

    private static ExternalPort external(Level level, BlockPos pos, Direction side) {
        if (!BuildCraftApi.service(BuildCraftServices.ENERGY).automaticFeConversionEnabled()) return null;
        EnergyStorage storage = EnergyStorage.SIDED.find(level, pos, side);
        return storage == null || (!storage.supportsInsertion() && !storage.supportsExtraction()) ? null
            : new ExternalPort(storage, () -> BuildCraftApi.service(BuildCraftServices.ENERGY).conversion().microMjPerFe(),
                () -> BuildCraftApi.service(BuildCraftServices.ENERGY).automaticFeConversionEnabled());
    }

    private static MjPortDescriptor describe(MjPort port) {
        EnumSet<MjPortRole> roles = EnumSet.of(MjPortRole.CONNECTOR, MjPortRole.READABLE);
        if (port.canInsert()) roles.add(MjPortRole.CONSUMER);
        if (port.canExtract()) roles.add(port instanceof ExternalPort ? MjPortRole.PROVIDER : MjPortRole.PASSIVE_PROVIDER);
        return new MjPortDescriptor(NETWORK_ID, roles, port.canInsert() ? UNKNOWN_RATE : MjAmount.ZERO,
            port.canExtract() ? UNKNOWN_RATE : MjAmount.ZERO);
    }

    /** Public for native conversion probes; the policy suppliers remain live across config reloads. */
    public static final class ExternalPort implements MjPort {
        private final EnergyStorage storage;
        private final LongSupplier ratio;
        private final BooleanSupplier enabled;

        public ExternalPort(EnergyStorage storage, LongSupplier ratio, BooleanSupplier enabled) {
            this.storage = Objects.requireNonNull(storage, "storage");
            this.ratio = Objects.requireNonNull(ratio, "ratio");
            this.enabled = Objects.requireNonNull(enabled, "enabled");
        }

        private long ratio() {
            long value = ratio.getAsLong();
            if (value <= 0) throw new IllegalStateException("MJ/FE conversion ratio must be positive");
            return value;
        }

        @Override public MjTransferResult insert(MjAmount offered, OperationMode mode) {
            return transfer(offered, mode, true);
        }

        @Override public MjTransferResult extract(MjAmount requested, OperationMode mode) {
            return transfer(requested, mode, false);
        }

        private MjTransferResult transfer(MjAmount amount, OperationMode mode, boolean inserting) {
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(mode, "mode");
            if (inserting ? !canInsert() : !canExtract()) return MjTransferResult.none(amount);
            long conversion = ratio();
            long whole = Math.min(Integer.MAX_VALUE, amount.microMj() / conversion);
            if (whole == 0) return MjTransferResult.none(amount);
            long moved = FabricTransferOperations.transfer(whole, mode == OperationMode.SIMULATE,
                transaction -> inserting ? storage.insert(whole, transaction) : storage.extract(whole, transaction));
            return MjTransferResult.of(amount, MjAmount.ofMicro(Math.multiplyExact(moved, conversion)));
        }

        @Override
        public MjTransferResult insert(MjAmount offered, MjTransferPolicy policy, OperationMode mode) {
            Objects.requireNonNull(offered, "offered");
            Objects.requireNonNull(policy, "policy");
            Objects.requireNonNull(mode, "mode");
            if (policy == MjTransferPolicy.PARTIAL) return insert(offered, mode);
            try (var operation = FabricTransferOperations.openTransaction()) {
                MjTransferResult result = insert(offered, OperationMode.EXECUTE);
                if (!result.completed()) return MjTransferResult.none(offered);
                if (mode == OperationMode.EXECUTE) operation.commit();
                return result;
            }
        }

        @Override
        public MjTransferResult extract(MjAmount minimum, MjAmount maximum, OperationMode mode) {
            Objects.requireNonNull(minimum, "minimum");
            Objects.requireNonNull(maximum, "maximum");
            Objects.requireNonNull(mode, "mode");
            if (minimum.compareTo(maximum) > 0) throw new IllegalArgumentException("Invalid MJ extraction range");
            try (var operation = FabricTransferOperations.openTransaction()) {
                MjTransferResult result = extract(maximum, OperationMode.EXECUTE);
                if (result.transferred().compareTo(minimum) < 0) return MjTransferResult.none(maximum);
                if (mode == OperationMode.EXECUTE) operation.commit();
                return result;
            }
        }

        @Override public MjAmount stored() { return MjAmount.ofMicro(Math.multiplyExact(storage.getAmount(), ratio())); }
        @Override public MjAmount capacity() { return MjAmount.ofMicro(Math.multiplyExact(storage.getCapacity(), ratio())); }
        @Override public boolean canInsert() { return enabled.getAsBoolean() && storage.supportsInsertion(); }
        @Override public boolean canExtract() { return enabled.getAsBoolean() && storage.supportsExtraction(); }
    }
}
