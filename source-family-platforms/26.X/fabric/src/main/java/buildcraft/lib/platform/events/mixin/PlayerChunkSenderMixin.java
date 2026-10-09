package buildcraft.lib.platform.events.mixin;

import buildcraft.lib.platform.events.PlatformEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderMixin {
    @Inject(method = "sendChunk", at = @At("RETURN"))
    private static void buildcraft$chunkSent(ServerGamePacketListenerImpl connection, ServerLevel level,
            LevelChunk chunk, CallbackInfo callback) {
        PlatformEvents.fireChunkWatch(connection.player, level);
    }

    @Inject(method = "dropChunk", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V",
        shift = At.Shift.AFTER))
    private void buildcraft$chunkForgotten(ServerPlayer player, ChunkPos pos, CallbackInfo callback) {
        PlatformEvents.fireChunkUnwatch(player, pos);
    }
}
