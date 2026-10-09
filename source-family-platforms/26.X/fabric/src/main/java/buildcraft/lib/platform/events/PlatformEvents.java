package buildcraft.lib.platform.events;

import buildcraft.lib.net.BCNetworkSide;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/** Common subscriptions never resolve Minecraft client classes on a dedicated server. */
public final class PlatformEvents {
    public record ChunkTracking(ServerPlayer player, ServerLevel level, ChunkPos pos) {}
    private static final EnumMap<BCEvents.Phase, List<Consumer<BCEvents.PlayerTick>>> PLAYER_TICKS = phases();
    private static final EnumMap<BCEvents.Phase, List<Consumer<BCEvents.LevelTick>>> CLIENT_LEVEL_TICKS = phases();
    private static final List<Consumer<BCEvents.ChunkWatch>> CHUNK_WATCH = new CopyOnWriteArrayList<>();
    private static final List<Consumer<ChunkTracking>> CHUNK_UNWATCH = new CopyOnWriteArrayList<>();

    private PlatformEvents() {}

    private static <T> EnumMap<BCEvents.Phase, List<Consumer<T>>> phases() {
        EnumMap<BCEvents.Phase, List<Consumer<T>>> listeners = new EnumMap<>(BCEvents.Phase.class);
        for (BCEvents.Phase phase : BCEvents.Phase.values()) listeners.put(phase, new CopyOnWriteArrayList<>());
        return listeners;
    }

    public static boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    public static void serverTick(BCEvents.Phase selected, Consumer<BCEvents.ServerTick> handler) {
        Objects.requireNonNull(selected, "phase");
        Objects.requireNonNull(handler, "handler");
        if (selected == BCEvents.Phase.START) {
            ServerTickEvents.START_SERVER_TICK.register(server -> handler.accept(new BCEvents.ServerTick(selected)));
        } else {
            ServerTickEvents.END_SERVER_TICK.register(server -> handler.accept(new BCEvents.ServerTick(selected)));
        }
    }

    public static void levelTick(BCEvents.Phase selected, Consumer<BCEvents.LevelTick> handler) {
        Objects.requireNonNull(selected, "phase");
        Objects.requireNonNull(handler, "handler");
        if (selected == BCEvents.Phase.START) {
            ServerTickEvents.START_LEVEL_TICK.register(level -> handler.accept(
                new BCEvents.LevelTick(level, selected, BCNetworkSide.SERVER)));
        } else {
            ServerTickEvents.END_LEVEL_TICK.register(level -> handler.accept(
                new BCEvents.LevelTick(level, selected, BCNetworkSide.SERVER)));
        }
        CLIENT_LEVEL_TICKS.get(selected).add(handler);
    }

    /** Agent 4 calls this from the matching Fabric client level tick event. */
    public static void fireClientLevelTick(Level level, BCEvents.Phase phase) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(phase, "phase");
        if (!level.isClientSide()) throw new IllegalArgumentException("Expected a client level");
        BCEvents.LevelTick event = new BCEvents.LevelTick(level, phase, BCNetworkSide.CLIENT);
        for (Consumer<BCEvents.LevelTick> handler : CLIENT_LEVEL_TICKS.get(phase)) handler.accept(event);
    }

    public static void playerTick(BCEvents.Phase selected, Consumer<BCEvents.PlayerTick> handler) {
        PLAYER_TICKS.get(Objects.requireNonNull(selected, "phase")).add(Objects.requireNonNull(handler, "handler"));
    }

    /** The Player.tick mixin brackets the real player tick on both logical sides. */
    public static void firePlayerTick(Player player, BCEvents.Phase phase) {
        Objects.requireNonNull(player, "player");
        BCEvents.PlayerTick event = new BCEvents.PlayerTick(player, Objects.requireNonNull(phase, "phase"));
        for (Consumer<BCEvents.PlayerTick> handler : PLAYER_TICKS.get(phase)) handler.accept(event);
    }

    public static void entityJoin(Consumer<BCEvents.EntityJoin> handler) {
        Objects.requireNonNull(handler, "handler");
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> handler.accept(new BCEvents.EntityJoin(entity)));
    }

    public static void playerJoin(Consumer<ServerPlayer> handler) {
        ServerPlayerEvents.JOIN.register(Objects.requireNonNull(handler, "handler")::accept);
    }

    public static void levelUnload(Consumer<BCEvents.LevelUnload> handler) {
        Objects.requireNonNull(handler, "handler");
        ServerLevelEvents.UNLOAD.register((server, level) -> handler.accept(new BCEvents.LevelUnload(level)));
    }

    public static void chunkUnload(Consumer<BCEvents.ChunkUnload> handler) {
        Objects.requireNonNull(handler, "handler");
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> handler.accept(new BCEvents.ChunkUnload(level, chunk.getPos())));
    }

    public static void chunkWatch(Consumer<BCEvents.ChunkWatch> handler) {
        CHUNK_WATCH.add(Objects.requireNonNull(handler, "handler"));
    }

    public static void chunkUnwatch(Consumer<ChunkTracking> handler) {
        CHUNK_UNWATCH.add(Objects.requireNonNull(handler, "handler"));
    }

    public static void fireChunkWatch(ServerPlayer player, ServerLevel level) {
        BCEvents.ChunkWatch event = new BCEvents.ChunkWatch(Objects.requireNonNull(player), Objects.requireNonNull(level));
        for (Consumer<BCEvents.ChunkWatch> handler : CHUNK_WATCH) handler.accept(event);
    }

    public static void fireChunkUnwatch(ServerPlayer player, ChunkPos pos) {
        Objects.requireNonNull(player, "player");
        ChunkTracking event = new ChunkTracking(player, player.level(), Objects.requireNonNull(pos, "pos"));
        for (Consumer<ChunkTracking> handler : CHUNK_UNWATCH) handler.accept(event);
    }

    public static void serverStarting(Consumer<MinecraftServer> handler) {
        ServerLifecycleEvents.SERVER_STARTING.register(Objects.requireNonNull(handler, "handler")::accept);
    }

    public static void serverStopped(Consumer<MinecraftServer> handler) {
        ServerLifecycleEvents.SERVER_STOPPED.register(Objects.requireNonNull(handler, "handler")::accept);
    }
}
