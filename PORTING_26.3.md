# Porting a NeoForge mod from Minecraft 26.1.2 to 26.3

General notes, gathered while porting BuildCraft. Target: NeoForge 26.3.0.52-beta.

- Avoid 26.3.0.56-beta for now: NeoForge PR #3584 moved `VersionChecker` from FML into NeoForge, and `getResult` now returns null instead of a pending placeholder. `ClientHooks.renderMainMenu` switches on that null and crashes the title screen in every client.
Items marked (open) do not have a confirmed replacement yet.

## Toolchain

- Java 25. ModDevGradle 2.0.148 or newer (2.0.143 fails in `createMinecraftArtifacts` with a HolderSet `contents()` access error).
- Run Gradle on JDK 25. JDK 27 fails with "Unsupported class file major version 71".
- Pack formats: resource pack 97.1, data pack 121. Network protocol 777.
- FML 12 `ModConfig.Type`: `COMMON` -> `LOCAL`, `SERVER` -> `SYNCED` (`CLIENT`, `STARTUP` unchanged).

## NeoForge storage APIs (removed)

- Gone: `net.neoforged.neoforge.items.*` (`IItemHandler`, `ItemStackHandler`, `SlotItemHandler`, wrappers), `energy.*` (`IEnergyStorage`), `fluids.capability.*` (`IFluidHandler`, `FluidTank`), `fluids.FluidUtil`, `fluids.IFluidTank`.
- Use the Transfer API: `ResourceHandler<ItemResource>` / `ResourceHandler<FluidResource>`, `EnergyHandler`, `Transaction.open(...)`, `ItemAccess.forPlayerInteraction(player, hand)` / `ItemAccess.forHandlerIndex(...)`, `transfer.fluid.FluidUtil` (`tryPlaceFluid`, `triggerSoundAndGameEvent`).
- Capabilities: `Capabilities.Item/Fluid/Energy.*`. Fluid handlers on items: `Capabilities.Fluid.ITEM`.
- Slot copy helper: `net.neoforged.neoforge.world.inventory.StackCopySlot`.
- `BiomeModifier.modify(RegistryAccess, Holder<Biome>, Phase, Builder)` - new first parameter.

## Vanilla gameplay

- `HoeItem`, `AxeItem`, `ShovelItem` removed. Use `ItemTags.HOES/AXES/SHOVELS` or `ItemAbility` checks.
- `FuelValues` / `level.fuelValues()` removed. Fuel is the `DataComponents.COOKING_FUEL` component (`CookingFuel`, burn time is a `ResolvableInt`).
  - Resolve: `ResolvableInt.getFromItem(stack, COOKING_FUEL, CookingFuel::burnTime, lootContext, 0)`.
  - Furnaces build the context with `LootContextParamSets.CONTAINER_PROCESS`. `ResolvableInt.Constant` needs no context.
- `PotionBrewing` removed. Brewing is `RecipeType.BREWING` (`BrewingRecipe`). Brewing fuel is `DataComponents.BREWING_FUEL`.
- Advancements: `advancements.Criterion` and all `advancements.criterion.*Trigger` moved to `advancements.triggers.*`. Predicates (`ItemPredicate`, ...) moved to `advancements.predicates.*`.
- `RecipeUnlockedTrigger.unlocked(ResourceKey)` -> `unlocked(Holder<Recipe>)`. In datagen: `output.lookup(Registries.RECIPE).getOrThrow(key)`.
- Worldgen: `Feature<FC>`, `FeatureConfiguration`, `FeaturePlaceContext` removed. `ConfiguredFeature` merged into `Feature`.
  - `Feature` is an interface: `MapCodec<? extends Feature> codec()` and `place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos)`.
  - The config lives on the feature instance. Register the `MapCodec` in `worldgen/feature_type` (`Registries.FEATURE_TYPE`).
  - Oil placement lives in the version-free `OilDepositPlacer`. Each `OilGenFeature` is a thin adapter: the shared one for `Feature<FC>`, a `//? source if >=26.3` one in `source-families/26.X`.
  - Data: `worldgen/configured_feature/*.json` becomes `worldgen/feature/*.json`, config fields flattened next to `"type"` (no `"config"` object). Inline features inside `placed_feature` are flattened the same way.
- `BlockState.CODEC` JSON: `{"Name": id, "Properties": {...}}` -> plain `"id"` for the default state, else `{"id": id, "properties": {...}}`.
- Advancements: `minecraft:recipe_unlocked` conditions `"recipe": id` -> `"recipes": id-or-list-or-#tag` (a recipe `HolderSet`). Fails at registry load, not at compile time.
- `TestData` gained `ResourceKey<Level> dimension` as its second component (codec default `Level.OVERWORLD`).
- `LivingEntity.swing(hand)` -> `swing(hand, SwingAnimation, boolean sendToSelf)`. Animation: `stack.getInteractAnimation()`. Old behaviour = `false`.
- `swingingArm` is private swing state; `swing(...)` records it.
- `Player.drop(stack, thrown)` / `drop(stack, false, thrown)` -> `drop(stack, thrown, Prediction)`. Server-side: `Prediction.SERVER_ONLY`.
- `Block.spawnDestroyParticles(Level, Player, BlockPos, BlockState)` -> public `spawnDestroyParticles(Level, BlockPos, BlockState)`.
- `Block.playerDestroy` takes `ServerLevel` and `ServerPlayer`.
- `EntityType.create(tag, level, EntitySpawnReason)` -> `create(tag, level, new EntitySpawnRequest(reason, false))`.
- `new BlockPos(Vec3i)` removed. Use the int constructor.
- `BlockState.blocksMotion()` removed. Old semantics: `block != COBWEB && block != BAMBOO_SAPLING && state.isSolid()`.
- `PushReaction`: `NORMAL` -> `PUSH_PULL`, `DESTROY` -> `POPPED`, `BLOCK` -> `IMMOVEABLE`, `PUSH_ONLY` -> `PUSH`, `IGNORE` -> `IGNORE_ENTITY`.
- `BlockTags.{DIAMOND,REDSTONE,LAPIS,COAL,EMERALD}_ORES` removed. Use `BlockItemTags.X.block()`.
- `ChatFormatting.getById`, `isColor`, `isFormat` removed. The 16 colours are ordinals 0-15.
- `I18n.exists(key)` -> `Language.getInstance().has(key)`.

## Client and rendering

- Render API moved to `com.mojang.renderpearl`:
  - `VertexFormat` -> `com.mojang.renderpearl.api.vertex.VertexFormat`. `DefaultVertexFormat` still exists.
  - `RenderPipeline` -> `com.mojang.renderpearl.api.pipeline.RenderPipeline`.
  - `VertexFormat.Mode` -> `com.mojang.renderpearl.api.pipeline.PrimitiveTopology`.
  - `GlStateManager` -> `com.mojang.renderpearl.backend.opengl.GlStateManager`.
- Removed: `MultiBufferSource`, `Tesselator`, `VertexMultiConsumer`, `VertexFormatElement`, `RenderBuffers.bufferSource()`.
- `BufferBuilder(ByteBufferBuilder, PrimitiveTopology, VertexFormat)`. Immediate drawing via `Tesselator`/`BufferUploader` is gone.
- World geometry: no immediate level buffer.
  - Replace `RenderLevelStageEvent.AfterTranslucentBlocks` drawing with `SubmitCustomGeometryEvent`.
  - Use `event.getSubmitNodeCollector().submitCustomGeometry(poseStack, renderType, (pose, consumer) -> ...)` and `event.getPoseStack()`.
  - BuildCraft records vertices into its own `MultiBufferSource` shim during the event and submits one custom geometry node per render type.
  - World renderers register through `buildcraft.core.client.WorldGeometryEvents`. Only that class knows which event the target uses.
- `VertexConsumer` has a new abstract `setUv3(float, float)`.
- `neoforge:fluid_container` item models: do not use the `cover` texture with `cover_is_mask: true` on 26.1+. The cover pass draws grey fragments and misplaces the fluid in GUI rendering; the `fluid` mask alone is enough.
- `PoseStack.mulPose(Quaternionfc)` -> `rotate(Quaternionfc)`. `mulPose(Matrix4fc)` is unchanged.
- `GameRenderer.getMainCamera()` -> `mainCamera()`.
- `BakedQuad.MaterialInfo`:
  - Before: `(sprite, layer, itemRenderType, tintIndex, boolean shade, lightEmission, ao)`.
  - After: `(sprite, layer, itemRenderType, itemGlintRenderType, itemGlintSpecialRenderType, tintIndex, @Nullable Direction shadeDirectionOverride, lightEmission, ao)`.
  - `shade == true` maps to `null` (shade by own face). Unshaded maps to `Direction.UP`.
  - Glint types: `Sheets.{cutout,translucent}[Block]Item{Glint,GlintSpecial}Sheet()`. `MaterialInfo.of(Material.Baked, Transparency, ...)` picks them automatically.
- `Sheets.cutoutBlockSheet()` removed. For item layers use `Sheets.cutoutBlockItemSheet()`.
- GLFW replaced by SDL3 (`org.lwjgl.glfw` gone).
  - Key values are SDL scancodes: use `InputConstants.KEY_*` and never raw GLFW numbers.
  - `GLFW_KEY_ENTER` -> `KEY_RETURN`, `GLFW_KEY_EQUAL` -> `KEY_EQUALS`, `GLFW_KEY_KP_ADD` -> `KEY_ADD`.
  - There is no `KEY_SUBTRACT`. Use `SDLScancode.SDL_SCANCODE_KP_MINUS`.
- `KeyEvent(key, scancode, modifiers)` -> `KeyEvent(key, keycode, modifiers)`. `scancode()` -> `keycode()`.
- The current screen moved from `Minecraft` to `Gui`: `mc.screen` -> `mc.gui.screen()`, `mc.setScreen(s)` -> `mc.gui.setScreen(s)`.
- `Util.getPlatform().openUri(uri)` -> `com.mojang.blaze3d.Blaze3D.openUri(uri)`.
- `AbstractContainerScreen`: `getGuiLeft/getGuiTop/getXSize/getYSize` -> `getLeftPos/getTopPos/getImageWidth/getImageHeight`.
