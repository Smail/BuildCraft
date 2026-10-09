package buildcraft.lib.platform.permission;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;
import com.mojang.authlib.GameProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

/** Automation invokes Fabric protection callbacks with the actual execution actor. */
public final class PlatformWorldActions {
    private static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");
    private static final GameProfile SYSTEM_PROFILE = new GameProfile(
        UUID.nameUUIDFromBytes("buildcraft.core".getBytes(StandardCharsets.UTF_8)), "[BuildCraft]");

    private PlatformWorldActions() {}

    public static boolean canBreakBlock(ServerLevel level, BlockPos pos, Player actor) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        if (actor == null || actor.isSpectator() || !level.mayInteract(actor, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        try {
            if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, actor, pos, state, entity)) {
                PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(level, actor, pos, state, entity);
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            LOGGER.warn("Protection callback failed while checking BuildCraft block break at {}", pos, exception);
            return false;
        }
    }

    public static boolean placeBlock(Level level, BlockPos pos, BlockState state, @Nullable Player actor,
                                     Direction placedAgainst, int flags) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(placedAgainst, "placedAgainst");
        if (!(level instanceof ServerLevel server)) {
            return level.setBlock(pos, state, flags);
        }
        Player placementActor = actor == null ? FakePlayer.get(server, SYSTEM_PROFILE) : actor;
        if (placementActor.isSpectator() || !server.mayInteract(placementActor, pos)) {
            return false;
        }
        BlockPos against = pos.relative(placedAgainst.getOpposite());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(against), placedAgainst, against, false);
        // Fabric exposes a cancellable use callback rather than NeoForge's
        // post-placement event. Check it before mutating the block or its data.
        try {
            if (UseBlockCallback.EVENT.invoker().interact(placementActor, server, InteractionHand.MAIN_HAND, hit)
                != InteractionResult.PASS) {
                return false;
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Protection callback failed while checking BuildCraft placement at {}", pos, exception);
            return false;
        }
        BlockState before = server.getBlockState(pos);
        BlockEntity entity = server.getBlockEntity(pos);
        CompoundTag saved = entity == null ? null : entity.saveWithFullMetadata(server.registryAccess());
        try {
            if (server.setBlock(pos, state, flags)) {
                return true;
            }
            restore(server, pos, before, saved, flags);
            return false;
        } catch (RuntimeException exception) {
            try {
                restore(server, pos, before, saved, flags);
            } catch (RuntimeException restorationFailure) {
                exception.addSuppressed(restorationFailure);
            }
            throw exception;
        }
    }

    private static void restore(ServerLevel level, BlockPos pos, BlockState state, @Nullable CompoundTag data, int flags) {
        if (level.getBlockState(pos) != state && !level.setBlock(pos, state, flags)) {
            throw new IllegalStateException("Could not restore canceled BuildCraft placement at " + pos);
        }
        if (data != null) {
            BlockEntity entity = BlockEntity.loadStatic(pos, state, data, level.registryAccess());
            if (entity == null) {
                throw new IllegalStateException("Could not restore block entity at " + pos);
            }
            level.setBlockEntity(entity);
        }
    }
}
