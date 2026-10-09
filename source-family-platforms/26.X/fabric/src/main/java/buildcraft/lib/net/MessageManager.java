package buildcraft.lib.net;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.lib.internal.module.IBuildCraftMod;
import buildcraft.lib.platform.server.PlatformServer;
import io.netty.handler.codec.DecoderException;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Fabric transport for the existing BuildCraft message catalogue. */
public final class MessageManager {
    public static final String PROTOCOL_VERSION = BuildCraftTarget.NETWORK_PROTOCOL;
    public static final boolean DEBUG = Boolean.getBoolean("buildcraft.debug.lib.messages");
    private static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");
    private static final int MAX_MESSAGE_CLASS_NAME = 256;
    static final CustomPacketPayload.Type<BuildCraftPayload> TYPE = new CustomPacketPayload.Type<>(
        Identifier.fromNamespaceAndPath("buildcraftlib", "messages"));
    private static final StreamCodec<RegistryFriendlyByteBuf, BuildCraftPayload> CODEC =
        StreamCodec.of(MessageManager::encodePayload, MessageManager::decodePayload);
    private static final Map<Class<?>, MessageInfo<?>> MESSAGES = new ConcurrentHashMap<>();
    private static final Map<String, MessageInfo<?>> MESSAGES_BY_NAME = new ConcurrentHashMap<>();
    private static boolean registered;
    private static java.util.function.Consumer<BuildCraftPayload> clientSender;

    private MessageManager() {}

    private static final class MessageInfo<I> {
        final IBuildCraftMod module;
        final Class<I> type;
        final BiConsumer<I, ? super RegistryFriendlyByteBuf> encoder;
        final Function<? super RegistryFriendlyByteBuf, I> decoder;
        volatile BiConsumer<I, Supplier<BCPacketContext>> clientHandler;
        volatile BiConsumer<I, Supplier<BCPacketContext>> serverHandler;

        MessageInfo(IBuildCraftMod module, Class<I> type,
                    BiConsumer<I, ? super RegistryFriendlyByteBuf> encoder,
                    Function<? super RegistryFriendlyByteBuf, I> decoder) {
            this.module = module;
            this.type = type;
            this.encoder = encoder;
            this.decoder = decoder;
        }
    }

    public record BuildCraftPayload(Object message) implements CustomPacketPayload {
        public BuildCraftPayload {
            Objects.requireNonNull(message, "message");
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static <I> void registerMessageClass(IBuildCraftMod module, Class<I> type,
        BiConsumer<I, ? super RegistryFriendlyByteBuf> encoder,
        Function<? super RegistryFriendlyByteBuf, I> decoder, BCNetworkSide... sides) {
        registerMessageClass(module, type, null, encoder, decoder, sides);
    }

    public static <I> void registerClientboundMessageClass(IBuildCraftMod module, Class<I> type,
        @Nullable BiConsumer<I, Supplier<BCPacketContext>> handler,
        BiConsumer<I, ? super RegistryFriendlyByteBuf> encoder,
        Function<? super RegistryFriendlyByteBuf, I> decoder) {
        registerMessageClass(module, type, handler, encoder, decoder, BCNetworkSide.CLIENT);
    }

    public static synchronized <I> void registerMessageClass(IBuildCraftMod module, Class<I> type,
        @Nullable BiConsumer<I, Supplier<BCPacketContext>> handler,
        BiConsumer<I, ? super RegistryFriendlyByteBuf> encoder,
        Function<? super RegistryFriendlyByteBuf, I> decoder, BCNetworkSide... sides) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(encoder, "encoder");
        Objects.requireNonNull(decoder, "decoder");
        MessageInfo<I> info = infoOrNull(type);
        if (info == null) {
            info = new MessageInfo<>(module, type, encoder, decoder);
            MESSAGES.put(type, info);
            MESSAGES_BY_NAME.put(type.getName(), info);
        } else if (!info.module.getModId().equals(module.getModId())) {
            throw new IllegalArgumentException("Message is already owned by " + info.module.getModId() + ": " + type);
        }
        if (handler != null) {
            BCNetworkSide only = sides != null && sides.length == 1 ? sides[0] : null;
            if (only == null || only == BCNetworkSide.CLIENT) {
                info.clientHandler = handler;
            }
            if (only == null || only == BCNetworkSide.SERVER) {
                info.serverHandler = handler;
            }
        }
    }

    public static <I> void setHandler(Class<I> type, BiConsumer<I, Supplier<BCPacketContext>> handler,
                                     BCNetworkSide side) {
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(side, "side");
        MessageInfo<I> info = requireInfo(type);
        if (side == BCNetworkSide.CLIENT) {
            info.clientHandler = handler;
        } else {
            info.serverHandler = handler;
        }
    }

    /** Install once from the common entrypoint, before client receiver installation. */
    public static synchronized void registerPayloads() {
        if (registered) {
            return;
        }
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
        FabricNetworkProtocol.register();
        if (!ServerPlayNetworking.registerGlobalReceiver(TYPE,
            (payload, context) -> handlePayload(payload, new FabricPacketContext(context)))) {
            throw new IllegalStateException("BuildCraft server receiver already registered");
        }
        PlatformServer.install();
        registered = true;
    }

    public static void fmlPostInit() {
        registerPayloads();
    }

    static void installClientSender(java.util.function.Consumer<BuildCraftPayload> sender) {
        clientSender = Objects.requireNonNull(sender, "sender");
    }

    private static void encodePayload(RegistryFriendlyByteBuf buffer, BuildCraftPayload payload) {
        MessageInfo<Object> info = requireInfo(payload.message().getClass());
        buffer.writeUtf(info.type.getName(), MAX_MESSAGE_CLASS_NAME);
        info.encoder.accept(payload.message(), buffer);
    }

    private static BuildCraftPayload decodePayload(RegistryFriendlyByteBuf buffer) {
        String name = buffer.readUtf(MAX_MESSAGE_CLASS_NAME);
        MessageInfo<?> info = MESSAGES_BY_NAME.get(name);
        if (info == null) {
            throw new DecoderException("Unregistered BuildCraft message: " + name);
        }
        Object decoded = Objects.requireNonNull(info.decoder.apply(buffer), "Decoded message");
        NetworkSecurity.requireFullyRead(buffer, name);
        return new BuildCraftPayload(decoded);
    }

    static void handlePayload(BuildCraftPayload payload, BCPacketContext context) {
        MessageInfo<Object> info = requireInfo(payload.message().getClass());
        BiConsumer<Object, Supplier<BCPacketContext>> handler = context.side().isClient()
            ? info.clientHandler : info.serverHandler;
        if (handler == null || (context.side().isServer() && context.getSender() == null)) {
            LOGGER.debug("Dropped BuildCraft message {} on {}", info.type.getName(), context.side());
            return;
        }
        runSafely(() -> handler.accept(payload.message(), () -> context), context.side());
    }

    static void runSafely(Runnable task, BCNetworkSide side) {
        try {
            task.run();
        } catch (RuntimeException exception) {
            if (side.isClient()) {
                LOGGER.warn("Failed to handle BuildCraft server payload", exception);
            } else {
                LOGGER.debug("Dropped invalid BuildCraft client payload", exception);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <I> MessageInfo<I> infoOrNull(Class<?> type) {
        return (MessageInfo<I>) MESSAGES.get(Objects.requireNonNull(type, "type"));
    }

    private static <I> MessageInfo<I> requireInfo(Class<?> type) {
        MessageInfo<I> info = infoOrNull(type);
        if (info == null) {
            throw new IllegalArgumentException("Unregistered BuildCraft message: " + type.getName());
        }
        return info;
    }

    private static BuildCraftPayload payload(Object message) {
        Objects.requireNonNull(message, "message");
        requireInfo(message.getClass());
        return new BuildCraftPayload(message);
    }

    private static MinecraftServer requireServer() {
        MinecraftServer current = PlatformServer.getCurrentServer();
        if (current == null) {
            throw new IllegalStateException("Cannot send BuildCraft packets without a running server");
        }
        return current;
    }

    public static void sendToAll(Object message) {
        BuildCraftPayload payload = payload(message);
        for (ServerPlayer player : PlayerLookup.all(requireServer())) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void sendTo(Object message, ServerPlayer player) {
        ServerPlayNetworking.send(Objects.requireNonNull(player, "player"), payload(message));
    }

    public static void sendToServer(Object message) {
        var sender = clientSender;
        if (sender == null) {
            throw new IllegalStateException("BuildCraft client transport has not been installed");
        }
        sender.accept(payload(message));
    }

    public static void sendToAllWatching(Object message, LevelChunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        if (!(chunk.getLevel() instanceof ServerLevel level)) {
            throw new IllegalStateException("Cannot send clientbound packets from a client level");
        }
        BuildCraftPayload payload = payload(message);
        for (ServerPlayer player : PlayerLookup.tracking(level, chunk.getPos())) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void sendToDimension(Object message, ResourceKey<Level> dimension) {
        ServerLevel level = requireServer().getLevel(Objects.requireNonNull(dimension, "dimension"));
        BuildCraftPayload payload = payload(message);
        if (level != null) {
            for (ServerPlayer player : level.players()) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
