package buildcraft.lib.fake;

import java.util.Objects;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.fabricmc.fabric.api.entity.FakePlayer;

/** Fabric supplies the disconnected listener and suppresses menus and sign editors. */
public final class FakePlayerBC extends FakePlayer {
    public FakePlayerBC(ServerLevel level, GameProfile profile) {
        super(Objects.requireNonNull(level, "level"), Objects.requireNonNull(profile, "profile"));
    }
}
