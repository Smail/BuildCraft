package buildcraft.lib.net;

import net.minecraft.network.chat.Component;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Install from the client entrypoint after common payload registration. */
@Environment(EnvType.CLIENT)
public final class FabricClientNetworking {
    private static boolean registered;

    private FabricClientNetworking() {}

    public static synchronized void register() {
        if (registered) {
            return;
        }
        MessageManager.registerPayloads();
        if (!ClientConfigurationNetworking.registerGlobalReceiver(FabricNetworkProtocol.TYPE, (payload, context) -> {
            if (!MessageManager.PROTOCOL_VERSION.equals(payload.version())) {
                context.responseSender().disconnect(
                    Component.literal("Incompatible BuildCraft network protocol"));
                return;
            }
            context.responseSender().sendPacket(new FabricNetworkProtocol.ProtocolPayload(MessageManager.PROTOCOL_VERSION));
        })) {
            throw new IllegalStateException("BuildCraft client protocol receiver already registered");
        }
        if (!ClientPlayNetworking.registerGlobalReceiver(MessageManager.TYPE, (payload, context) -> {
            var connection = context.client().getConnection();
            var packetContext = new FabricPacketContext(BCNetworkSide.CLIENT, context.player(), context.client(),
                () -> connection != null && context.client().getConnection() == connection
                    && connection.getConnection().isConnected());
            MessageManager.handlePayload(payload, packetContext);
        })) {
            throw new IllegalStateException("BuildCraft client play receiver already registered");
        }
        MessageManager.installClientSender(ClientPlayNetworking::send);
        registered = true;
    }
}
