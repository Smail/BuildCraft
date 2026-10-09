package buildcraft.lib.net;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationPacketListenerImpl;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;

/** Require the existing BuildCraft protocol before either peer enters play. */
final class FabricNetworkProtocol {
    static final CustomPacketPayload.Type<ProtocolPayload> TYPE = new CustomPacketPayload.Type<>(
        Identifier.fromNamespaceAndPath("buildcraftlib", "protocol"));
    private static final ConfigurationTask.Type TASK = new ConfigurationTask.Type("buildcraftlib:protocol");
    private static final Set<ServerConfigurationPacketListenerImpl> PENDING = ConcurrentHashMap.newKeySet();
    private static final StreamCodec<FriendlyByteBuf, ProtocolPayload> CODEC = StreamCodec.of(
        (buffer, payload) -> buffer.writeUtf(payload.version(), 128),
        buffer -> {
            String version = buffer.readUtf(128);
            NetworkSecurity.requireFullyRead(buffer, "BuildCraft protocol");
            return new ProtocolPayload(version);
        });

    private FabricNetworkProtocol() {}

    record ProtocolPayload(String version) implements CustomPacketPayload {
        ProtocolPayload {
            Objects.requireNonNull(version, "version");
            if (version.length() > 128) {
                throw new IllegalArgumentException("BuildCraft protocol version is too long");
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register() {
        PayloadTypeRegistry.clientboundConfiguration().register(TYPE, CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(TYPE, CODEC);
        if (!ServerConfigurationNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
            var listener = context.packetListener();
            if (!PENDING.remove(listener) || !MessageManager.PROTOCOL_VERSION.equals(payload.version())) {
                listener.disconnect(Component.literal("Incompatible BuildCraft network protocol"));
                return;
            }
            try {
                ((FabricServerConfigurationPacketListenerImpl) listener).completeTask(TASK);
            } catch (RuntimeException exception) { buildcraft.lib.internal.debug.BCLog.caught("FabricNetworkProtocol.register", exception);
                listener.disconnect(Component.literal("BuildCraft protocol acknowledgement arrived out of order"));
            }
        })) {
            throw new IllegalStateException("BuildCraft protocol receiver already registered");
        }
        ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
            if (!ServerConfigurationNetworking.canSend(listener, TYPE)) {
                listener.disconnect(Component.literal("This server requires compatible BuildCraft networking"));
                return;
            }
            ((FabricServerConfigurationPacketListenerImpl) listener).addTask(new ConfigurationTask() {
                @Override
                public void start(Consumer<Packet<?>> sender) {
                    PENDING.add(listener);
                    try {
                        sender.accept(ServerConfigurationNetworking.createClientboundPacket(
                            new ProtocolPayload(MessageManager.PROTOCOL_VERSION)));
                    } catch (RuntimeException exception) {
                        PENDING.remove(listener);
                        listener.disconnect(Component.literal("Could not send BuildCraft protocol negotiation"));
                        throw exception;
                    }
                }

                @Override
                public Type type() {
                    return TASK;
                }
            });
        });
        ServerConfigurationConnectionEvents.DISCONNECT.register((listener, server) -> PENDING.remove(listener));
    }
}
