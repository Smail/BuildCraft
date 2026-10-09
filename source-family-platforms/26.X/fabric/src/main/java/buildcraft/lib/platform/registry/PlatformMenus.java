package buildcraft.lib.platform.registry;

import io.netty.buffer.Unpooled;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** Carries existing BuildCraft opening bytes through Fabric's typed menu payload. */
public final class PlatformMenus {
    private static final int MAX_OPENING_BYTES = 1 << 20;

    private PlatformMenus() {}

    public static <T extends AbstractContainerMenu> MenuType<T> create(BCMenuFactory<T> factory) {
        Objects.requireNonNull(factory, "factory");
        return new ExtendedMenuType<>((windowId, inventory, bytes) -> {
            FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
            try {
                return Objects.requireNonNull(factory.create(windowId, inventory, data), "Menu factory returned null");
            } catch (RuntimeException cause) {
                throw new IllegalStateException("Failed to create BuildCraft menu " + windowId, cause);
            } finally {
                data.release();
            }
        }, ByteBufCodecs.byteArray(MAX_OPENING_BYTES));
    }

    public static OptionalInt open(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> writer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(writer, "writer");
        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer());
        final byte[] bytes;
        try {
            writer.accept(data);
            if (data.readableBytes() > MAX_OPENING_BYTES) {
                throw new IllegalArgumentException("BuildCraft menu opening payload exceeds " + MAX_OPENING_BYTES);
            }
            bytes = new byte[data.readableBytes()];
            data.getBytes(data.readerIndex(), bytes);
        } catch (RuntimeException cause) {
            throw new IllegalStateException("Failed to encode BuildCraft menu opening data", cause);
        } finally {
            data.release();
        }
        return player.openMenu(new ExtendedMenuProvider<byte[]>() {
            @Override
            public byte[] getScreenOpeningData(ServerPlayer recipient) {
                return bytes.clone();
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player recipient) {
                return provider.createMenu(windowId, inventory, recipient);
            }
        });
    }
    public static OptionalInt open(ServerPlayer player, MenuProvider provider) {
        return player.openMenu(Objects.requireNonNull(provider, "provider"));
    }
}
