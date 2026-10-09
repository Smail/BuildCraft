package buildcraft.lib.net;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;

import net.minecraft.world.entity.player.Player;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Packet work remains on its logical game thread and is discarded after disconnect. */
public final class FabricPacketContext implements BCPacketContext {
    private final BCNetworkSide side;
    private final @Nullable Player player;
    private final Executor executor;
    private final BooleanSupplier connected;

    public FabricPacketContext(ServerPlayNetworking.Context context) {
        this(BCNetworkSide.SERVER, context.player(), context.server(),
            () -> !context.player().hasDisconnected());
    }

    public FabricPacketContext(BCNetworkSide side, @Nullable Player player, Executor executor, BooleanSupplier connected) {
        this.side = Objects.requireNonNull(side, "side");
        this.player = player;
        this.executor = Objects.requireNonNull(executor, "executor");
        this.connected = Objects.requireNonNull(connected, "connected");
    }

    @Override
    public BCNetworkSide side() {
        return side;
    }

    @Override
    public @Nullable Player player() {
        return player;
    }

    @Override
    public void enqueueWork(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (connected.getAsBoolean()) {
            executor.execute(() -> {
                if (connected.getAsBoolean()) {
                    MessageManager.runSafely(task, side);
                }
            });
        }
    }

    @Override
    public void setPacketHandled(boolean handled) {
        // Fabric owns handled status through receiver registration.
    }
}
