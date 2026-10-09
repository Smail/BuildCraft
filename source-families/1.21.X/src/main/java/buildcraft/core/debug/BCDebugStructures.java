package buildcraft.core.debug;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.core.BCCoreBlocks;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.lib.block.BlockBCBase_Neptune;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.internal.enums.EnumEngineType;
import buildcraft.lib.internal.properties.BuildCraftProperties;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportPipes;
import buildcraft.transport.internal.pipe.IItemPipe;
import buildcraft.transport.internal.pipe.PipeApi;
import buildcraft.transport.internal.pipe.PipeDefinition;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Catalogue of large, ready-made BuildCraft machine layouts. The same builders back the {@code /bcdebug} commands and
 * the machine-chain GameTests, so whatever a developer builds by hand in-game is exactly what the tests exercise.
 * <p>
 * Every layout is described in local coordinates (x east, y up, z south, all non-negative) and is rotated around the
 * origin when built.
 */
public final class BCDebugStructures {
    /** Local footprint of a structure. */
    public record Size(int x, int y, int z) {
    }

    /** One buildable layout. */
    public interface Structure {
        String name();

        String description();

        Size size();

        void build(Builder builder);
    }

    /** Result of a build: named absolute positions of the interesting blocks plus the area that was touched. */
    public record Built(Structure structure, BlockPos origin, Rotation rotation, Map<String, BlockPos> anchors,
        BlockPos boundsMin, BlockPos boundsMax) {
        public BlockPos anchor(String name) {
            BlockPos pos = anchors.get(name);
            if (pos == null) {
                throw new IllegalStateException("Structure " + structure.name() + " has no anchor named '" + name
                    + "', known anchors: " + anchors.keySet());
            }
            return pos;
        }
    }

    /** Footprint of the quarry rigs: pipes, quarry and the default 11x5x11 frame behind it. */
    private static final int RIG_SIZE_X = 20;
    private static final int RIG_SIZE_Y = 9;
    private static final int RIG_SIZE_Z = 12;

    private static final Map<String, Structure> STRUCTURES = new LinkedHashMap<>();

    static {
        register(new QuarryRig("stirling_quarry", EnumEngineType.STONE,
            "3 stone (stirling) engines -> wooden kinesis pipe -> 4 gold kinesis pipes -> quarry -> item pipes -> chest"));
        register(new QuarryRig("creative_quarry", EnumEngineType.CREATIVE,
            "Same as stirling_quarry but with creative engines, so it needs no fuel and runs at full speed"));
        register(new ItemHub());
    }

    private BCDebugStructures() {
    }

    private static void register(Structure structure) {
        if (STRUCTURES.put(structure.name(), structure) != null) {
            throw new IllegalStateException("Duplicate debug structure " + structure.name());
        }
    }

    public static Map<String, Structure> all() {
        return Collections.unmodifiableMap(STRUCTURES);
    }

    public static Structure get(String name) {
        Structure structure = STRUCTURES.get(name);
        if (structure == null) {
            throw new IllegalArgumentException("Unknown debug structure '" + name + "', known: " + STRUCTURES.keySet());
        }
        return structure;
    }

    /**
     * Clears the footprint of the structure, then builds it.
     *
     * @param placer owner of every placed machine; must be a real or fake player so ownership and permissions work.
     * @throws IllegalStateException if a block did not produce the expected block entity.
     */
    public static Built build(ServerLevel level, Structure structure, BlockPos origin, Rotation rotation,
        LivingEntity placer) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(placer, "placer");
        clear(level, structure, origin, rotation);
        Builder builder = new Builder(level, origin, rotation, placer);
        structure.build(builder);
        BlockPos[] bounds = bounds(structure, origin, rotation);
        return new Built(structure, origin, rotation, Collections.unmodifiableMap(new LinkedHashMap<>(builder.anchors)),
            bounds[0], bounds[1]);
    }

    /** Removes every block, block entity content and dropped item in the footprint of the structure. */
    public static void clear(ServerLevel level, Structure structure, BlockPos origin, Rotation rotation) {
        Objects.requireNonNull(level, "level");
        BlockPos[] bounds = bounds(structure, origin, rotation);
        for (BlockPos pos : BlockPos.betweenClosed(bounds[0], bounds[1])) {
            BlockPos immutable = pos.immutable();
            if (level.isOutsideBuildHeight(immutable)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(immutable);
            if (blockEntity instanceof Clearable clearable) {
                clearable.clearContent();
            }
            if (!level.getBlockState(immutable).isAir()) {
                level.setBlock(immutable, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        AABB area = AABB.encapsulatingFullBlocks(bounds[0], bounds[1]);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            item.discard();
        }
    }

    /** @return true if every chunk touched by the structure is loaded, so blocks are never placed into void chunks. */
    public static boolean isAreaLoaded(ServerLevel level, Structure structure, BlockPos origin, Rotation rotation) {
        BlockPos[] bounds = bounds(structure, origin, rotation);
        return level.hasChunksAt(bounds[0], bounds[1]);
    }

    private static BlockPos[] bounds(Structure structure, BlockPos origin, Rotation rotation) {
        Size size = structure.size();
        BlockPos a = origin.offset(new BlockPos(0, 0, 0).rotate(rotation));
        BlockPos b = origin.offset(new BlockPos(size.x() - 1, size.y() - 1, size.z() - 1).rotate(rotation));
        return new BlockPos[] {
            new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
            new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()))
        };
    }

    /** Placement helper handed to {@link Structure#build}. All coordinates are local and rotated on the fly. */
    public static final class Builder {
        private final ServerLevel level;
        private final BlockPos origin;
        private final Rotation rotation;
        private final LivingEntity placer;
        private final Map<String, BlockPos> anchors = new LinkedHashMap<>();

        private Builder(ServerLevel level, BlockPos origin, Rotation rotation, LivingEntity placer) {
            this.level = level;
            this.origin = origin;
            this.rotation = rotation;
            this.placer = placer;
        }

        public ServerLevel level() {
            return level;
        }

        public BlockPos abs(int x, int y, int z) {
            return origin.offset(new BlockPos(x, y, z).rotate(rotation));
        }

        public Direction dir(Direction local) {
            return rotation.rotate(local);
        }

        public void anchor(String name, int x, int y, int z) {
            if (anchors.put(name, abs(x, y, z)) != null) {
                throw new IllegalStateException("Duplicate anchor " + name);
            }
        }

        public void block(int x, int y, int z, BlockState state) {
            BlockPos pos = abs(x, y, z);
            if (level.isOutsideBuildHeight(pos)) {
                throw new IllegalStateException("Structure block at " + pos + " is outside the build height");
            }
            if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
                throw new IllegalStateException("Could not place " + state + " at " + pos);
            }
        }

        public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                        block(x, y, z, state);
                    }
                }
            }
        }

        /** Places a vanilla chest and optionally fills its first slots. */
        public ChestBlockEntity chest(String anchorName, int x, int y, int z, @Nonnull ItemStack... contents) {
            block(x, y, z, Blocks.CHEST.defaultBlockState());
            BlockPos pos = abs(x, y, z);
            if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
                throw new IllegalStateException("Chest at " + pos + " did not create a ChestBlockEntity");
            }
            for (int slot = 0; slot < contents.length && slot < chest.getContainerSize(); slot++) {
                chest.setItem(slot, contents[slot]);
            }
            chest.setChanged();
            anchor(anchorName, x, y, z);
            return chest;
        }

        public void redstoneBlock(int x, int y, int z) {
            block(x, y, z, Blocks.REDSTONE_BLOCK.defaultBlockState());
        }

        public TilePipeHolder pipe(int x, int y, int z, PipeDefinition definition) {
            if (definition == null) {
                throw new IllegalStateException("Pipe definition is not registered yet (pipe registration has not run)");
            }
            block(x, y, z, BCTransportBlocks.pipeHolder.get().defaultBlockState());
            BlockPos pos = abs(x, y, z);
            if (!(level.getBlockEntity(pos) instanceof TilePipeHolder holder)) {
                throw new IllegalStateException("Pipe holder at " + pos + " did not create a TilePipeHolder");
            }
            IItemPipe itemPipe = PipeApi.pipeRegistry.getItemForPipe(definition);
            if (!(itemPipe instanceof Item item)) {
                throw new IllegalStateException("Pipe definition " + definition.identifier + " has no registered item");
            }
            holder.onPlacedBy(placer, new ItemStack(item));
            if (holder.getPipe() == Pipe.EMPTY) {
                throw new IllegalStateException("Placing the pipe item did not initialise a pipe at " + pos);
            }
            return holder;
        }

        /**
         * Places an engine. Stone engines are loaded with coal. The engine is only started once a redstone block is
         * placed next to it, see {@link #redstoneBlock}.
         */
        public TileEngineBase_BC8 engine(String anchorName, int x, int y, int z, EnumEngineType type) {
            BlockState state = BCCoreBlocks.ENGINE_BC8.get().defaultBlockState()
                .setValue(BuildCraftProperties.ENGINE_TYPE, type);
            block(x, y, z, state);
            BlockPos pos = abs(x, y, z);
            if (!(level.getBlockEntity(pos) instanceof TileEngineBase_BC8 engine)) {
                throw new IllegalStateException("Engine at " + pos + " did not create an engine block entity");
            }
            if (engine instanceof TileEngineStone_BC8 stone) {
                ItemStack leftover = stone.invFuel.insert(new ItemStack(Items.COAL, 64), false, false);
                if (!leftover.isEmpty()) {
                    throw new IllegalStateException("Stone engine at " + pos + " refused its coal");
                }
            }
            engine.onPlacedBy(placer, new ItemStack(Items.AIR));
            anchor(anchorName, x, y, z);
            return engine;
        }

        /** Places a quarry whose default frame extends away from the side it faces. */
        public TileQuarry quarry(String anchorName, int x, int y, int z, Direction localFacing) {
            BlockState state = BCBuildersBlocks.QUARRY.get().defaultBlockState()
                .setValue(BlockBCBase_Neptune.PROP_FACING, dir(localFacing));
            block(x, y, z, state);
            BlockPos pos = abs(x, y, z);
            if (!(level.getBlockEntity(pos) instanceof TileQuarry quarry)) {
                throw new IllegalStateException("Quarry at " + pos + " did not create a TileQuarry");
            }
            quarry.onPlacedBy(placer, new ItemStack(BCBuildersItems.QUARRY_BLOCK_ITEM.get()));
            anchor(anchorName, x, y, z);
            return quarry;
        }
    }

    /**
     * Engines around one wooden kinesis pipe, a run of gold kinesis pipes into a quarry, and an item pipe line from the
     * quarry to a chest.
     *
     * <pre>
     *   z=4        R
     *   z=5  R E W G G G G Q (frame eastwards, x 8..18, z 0..10)
     *   z=6        E          item pipes ... chest at (7, 3, 9)
     *   z=7        R
     * </pre>
     */
    private static final class QuarryRig implements Structure {
        /** Floor level of the rig: the quarry, pipes and engines sit here, the mining bed lies below. */
        static final int ROW_Y = 3;
        static final int ROW_Z = 5;

        private final String name;
        private final EnumEngineType engineType;
        private final String description;

        QuarryRig(String name, EnumEngineType engineType, String description) {
            this.name = name;
            this.engineType = engineType;
            this.description = description;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public Size size() {
            return new Size(RIG_SIZE_X, RIG_SIZE_Y, RIG_SIZE_Z);
        }

        @Override
        public void build(Builder b) {
            // Mining bed below the frame: dirt on top of two stone layers.
            b.fill(8, 0, 0, 18, 1, 10, Blocks.STONE.defaultBlockState());
            b.fill(8, 2, 0, 18, 2, 10, Blocks.DIRT.defaultBlockState());

            // Chest and its pipes first, so the quarry finds an acceptor as soon as it mines something.
            b.chest("chest", 7, ROW_Y, 9);
            b.pipe(7, ROW_Y, 8, BCTransportPipes.goldItem);
            b.pipe(7, ROW_Y, 7, BCTransportPipes.cobbleItem);
            b.pipe(7, ROW_Y, 6, BCTransportPipes.cobbleItem);

            // Power line: wooden pipe collects from three engines, gold pipes carry it to the quarry.
            b.pipe(2, ROW_Y, ROW_Z, BCTransportPipes.woodPower);
            b.anchor("wood_pipe", 2, ROW_Y, ROW_Z);
            for (int x = 3; x <= 6; x++) {
                b.pipe(x, ROW_Y, ROW_Z, BCTransportPipes.goldPower);
                b.anchor("gold_pipe_" + (x - 3), x, ROW_Y, ROW_Z);
            }

            b.engine("engine_0", 1, ROW_Y, ROW_Z, engineType);
            b.engine("engine_1", 2, ROW_Y, ROW_Z - 1, engineType);
            b.engine("engine_2", 2, ROW_Y, ROW_Z + 1, engineType);
            b.redstoneBlock(0, ROW_Y, ROW_Z);
            b.redstoneBlock(2, ROW_Y, ROW_Z - 2);
            b.redstoneBlock(2, ROW_Y, ROW_Z + 2);

            // The quarry faces the pipes, so its default frame extends east, away from them.
            b.quarry("quarry", 7, ROW_Y, ROW_Z, Direction.WEST);
        }
    }

    /**
     * A powered wooden item pipe pulls from a stocked chest and a gold pipe fans the stream out over three chests.
     *
     * <pre>
     *   z=1           C
     *   z=2  C W P P G P C        (W pulls, powered by a creative engine above it)
     *   z=3           C
     * </pre>
     */
    private static final class ItemHub implements Structure {
        public static final int SOURCE_COBBLE = 64;
        public static final int SOURCE_APPLES = 32;
        public static final int SOURCE_IRON = 16;
        public static final int SOURCE_TOTAL = SOURCE_COBBLE + SOURCE_APPLES + SOURCE_IRON;

        @Override
        public String name() {
            return "item_hub";
        }

        @Override
        public String description() {
            return "Stocked chest -> powered wooden pipe -> cobble pipes -> gold pipe -> 3 destination chests";
        }

        @Override
        public Size size() {
            return new Size(7, 4, 5);
        }

        @Override
        public void build(Builder b) {
            b.chest("source", 0, 0, 2,
                new ItemStack(Items.COBBLESTONE, SOURCE_COBBLE),
                new ItemStack(Items.APPLE, SOURCE_APPLES),
                new ItemStack(Items.IRON_INGOT, SOURCE_IRON));

            b.chest("sink_north", 4, 0, 1);
            b.chest("sink_south", 4, 0, 3);
            b.chest("sink_east", 6, 0, 2);

            b.pipe(1, 0, 2, BCTransportPipes.woodItem);
            b.pipe(2, 0, 2, BCTransportPipes.cobbleItem);
            b.pipe(3, 0, 2, BCTransportPipes.cobbleItem);
            b.pipe(4, 0, 2, BCTransportPipes.goldItem);
            b.pipe(5, 0, 2, BCTransportPipes.cobbleItem);

            b.engine("engine", 1, 1, 2, EnumEngineType.CREATIVE);
            b.redstoneBlock(1, 2, 2);
            b.anchor("wood_pipe", 1, 0, 2);
        }
    }
}
