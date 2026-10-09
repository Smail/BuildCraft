package buildcraft.lib.net;

import java.util.ArrayDeque;
import java.util.Queue;

/** Checks catalogue direction, game-thread scheduling and disconnect cleanup using real native types. */
public final class FabricNetworkProbe {
    private FabricNetworkProbe() {}

    public static void main(String[] args) {
        Queue<Runnable> tasks = new ArrayDeque<>();
        boolean[] connected = {true};
        int[] value = {0};
        FabricPacketContext client = new FabricPacketContext(BCNetworkSide.CLIENT, null, tasks::add, () -> connected[0]);
        MessageManager.registerClientboundMessageClass(() -> "probe", ProbeMessage.class,
            (message, context) -> context.get().enqueueWork(() -> value[0] = value[0] * 10 + message.value()),
            (message, buffer) -> buffer.writeInt(message.value()), buffer -> new ProbeMessage(buffer.readInt()));
        MessageManager.handlePayload(new MessageManager.BuildCraftPayload(new ProbeMessage(1)), client);
        MessageManager.handlePayload(new MessageManager.BuildCraftPayload(new ProbeMessage(2)), client);
        equal(0, value[0]);
        equal(2, tasks.size());
        tasks.remove().run();
        tasks.remove().run();
        equal(12, value[0]);
        MessageManager.handlePayload(new MessageManager.BuildCraftPayload(new ProbeMessage(3)), client);
        connected[0] = false;
        tasks.remove().run();
        equal(12, value[0]);
        client.enqueueWork(() -> value[0]++);
        equal(0, tasks.size());
        FabricPacketContext server = new FabricPacketContext(BCNetworkSide.SERVER, null, tasks::add, () -> true);
        MessageManager.handlePayload(new MessageManager.BuildCraftPayload(new ProbeMessage(9)), server);
        equal(0, tasks.size());
        connected[0] = true;
        client.enqueueWork(() -> { throw new IllegalArgumentException("invalid payload"); });
        client.enqueueWork(() -> value[0]++);
        tasks.remove().run();
        tasks.remove().run();
        equal(13, value[0]);
        expectFailure(() -> MessageManager.setHandler(String.class, (message, context) -> {}, BCNetworkSide.CLIENT));
        expectFailure(() -> MessageManager.registerClientboundMessageClass(() -> "another_mod", ProbeMessage.class,
            null, (message, buffer) -> buffer.writeInt(message.value()), buffer -> new ProbeMessage(buffer.readInt())));
        new FabricNetworkProtocol.ProtocolPayload(MessageManager.PROTOCOL_VERSION);
        expectFailure(() -> new FabricNetworkProtocol.ProtocolPayload("x".repeat(129)));
        System.out.println("Fabric network context probes passed");
    }

    private record ProbeMessage(int value) {}

    private static void equal(long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void expectFailure(Runnable task) {
        try {
            task.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected invalid operation to fail");
    }
}
