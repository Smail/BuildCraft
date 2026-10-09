package buildcraft.lib.platform.actor;

import java.util.Objects;

import com.mojang.authlib.GameProfile;

import buildcraft.lib.fake.FakePlayerBC;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.entity.FakePlayer;

/** BCActors owns the cache; each native actor belongs to exactly one world. */
public final class PlatformActors {
    private PlatformActors() {}

    public static ServerPlayer create(ServerLevel level, GameProfile profile) {
        return new FakePlayerBC(Objects.requireNonNull(level, "level"), Objects.requireNonNull(profile, "profile"));
    }

    public static void afterAcquire(ServerLevel level, GameProfile profile, ServerPlayer actor) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(actor, "actor");
        if (!(actor instanceof FakePlayer)) {
            throw new IllegalArgumentException("BuildCraft automation actor must be a Fabric fake player");
        }
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(profile.id());
        if (online != null && online != actor) {
            online.getAdvancements().setPlayer(online);
        }
    }
}
