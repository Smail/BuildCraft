package buildcraft.core.debug;

import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Rotation;

import buildcraft.core.debug.BCDebugStructures.Built;
import buildcraft.core.debug.BCDebugStructures.Structure;
import buildcraft.lib.misc.FakePlayerProvider;

/**
 * {@code /bcdebug}: builds and removes large test machines for in-game testing.
 * <pre>
 *   /bcdebug list
 *   /bcdebug build &lt;structure&gt; [&lt;pos&gt;] [&lt;rotation&gt;]
 *   /bcdebug clear &lt;structure&gt; [&lt;pos&gt;] [&lt;rotation&gt;]
 * </pre>
 * The position defaults to the block the command source stands in. Rotation is 0, 90, 180 or 270 degrees clockwise.
 */
public final class BCDebugCommands {
    private static final List<String> ROTATIONS = List.of("0", "90", "180", "270");
    private static final SimpleCommandExceptionType NOT_LOADED =
        new SimpleCommandExceptionType(Component.literal("The area for this structure is not fully loaded"));

    private BCDebugCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bcdebug")
            .requires(source -> source.getEntity() == null
                || (source.getEntity() instanceof Player player && player.hasPermissions(2)))
            .then(Commands.literal("list").executes(BCDebugCommands::list))
            .then(action("build", false))
            .then(action("clear", true)));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> action(String literal, boolean clear) {
        return Commands.literal(literal).then(Commands.argument("structure", StringArgumentType.word())
            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(BCDebugStructures.all().keySet(), builder))
            .executes(ctx -> run(ctx, clear, here(ctx), "0"))
            .then(Commands.argument("pos", BlockPosArgument.blockPos())
                .executes(ctx -> run(ctx, clear, BlockPosArgument.getLoadedBlockPos(ctx, "pos"), "0"))
                .then(Commands.argument("rotation", StringArgumentType.word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ROTATIONS, builder))
                    .executes(ctx -> run(ctx, clear, BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                        StringArgumentType.getString(ctx, "rotation"))))));
    }

    private static BlockPos here(CommandContext<CommandSourceStack> ctx) {
        return BlockPos.containing(ctx.getSource().getPosition());
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        for (Structure structure : BCDebugStructures.all().values()) {
            BCDebugStructures.Size size = structure.size();
            source.sendSuccess(() -> Component.literal(structure.name() + " (" + size.x() + "x" + size.y() + "x"
                + size.z() + "): " + structure.description()), false);
        }
        return BCDebugStructures.all().size();
    }

    private static int run(CommandContext<CommandSourceStack> ctx, boolean clear, BlockPos origin, String rotationText)
        throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "structure");
        Structure structure = BCDebugStructures.all().get(name);
        if (structure == null) {
            source.sendFailure(Component.literal("Unknown structure '" + name + "', try /bcdebug list"));
            return 0;
        }
        Rotation rotation = parseRotation(rotationText);
        if (rotation == null) {
            source.sendFailure(Component.literal("Rotation must be one of " + ROTATIONS + ", got '" + rotationText + "'"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        if (!BCDebugStructures.isAreaLoaded(level, structure, origin, rotation)) {
            throw NOT_LOADED.create();
        }
        try {
            if (clear) {
                BCDebugStructures.clear(level, structure, origin, rotation);
                source.sendSuccess(() -> Component.literal("Cleared " + name + " at " + origin.toShortString()), true);
                return 1;
            }
            Built built = BCDebugStructures.build(level, structure, origin, rotation, placer(source, level, origin));
            source.sendSuccess(() -> Component.literal("Built " + name + " at " + origin.toShortString() + ", "
                + built.anchors().size() + " tracked blocks, area " + built.boundsMin().toShortString() + " to "
                + built.boundsMax().toShortString()), true);
            return 1;
        } catch (RuntimeException e) { buildcraft.lib.internal.debug.BCLog.caught("BCDebugCommands.run", e);
            source.sendFailure(Component.literal("Failed to " + (clear ? "clear " : "build ") + name + ": " + e.getMessage()));
            return 0;
        }
    }

    private static LivingEntity placer(CommandSourceStack source, ServerLevel level, BlockPos origin) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        return FakePlayerProvider.INSTANCE.getFakePlayer(level, FakePlayerProvider.NULL_PROFILE, origin);
    }

    /** @return the rotation, or null for unknown text. */
    static Rotation parseRotation(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "0" -> Rotation.NONE;
            case "90" -> Rotation.CLOCKWISE_90;
            case "180" -> Rotation.CLOCKWISE_180;
            case "270" -> Rotation.COUNTERCLOCKWISE_90;
            default -> null;
        };
    }
}
