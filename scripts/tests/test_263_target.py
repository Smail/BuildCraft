#!/usr/bin/env python3
"""Structural and transform guard for the Minecraft 26.3 NeoForge target."""
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))

from source_config import load_properties, target_layout  # noqa: E402
from source_layout import effective_source_files  # noqa: E402
from transforms.java_compat import upgrade_263_symbols  # noqa: E402
from transforms.resources import (  # noqa: E402
    _dynamic_fluid_bucket_client_item_1_21_11,
    _flatten_263_feature,
    _upgrade_263_criteria,
)

TARGET = "26.3-neoforge"

# (description, source, fragments that must appear, fragments that must disappear)
TRANSFORM_CASES = (
    (
        "native inherited robotics API names",
        "import net.minecraft.world.inventory.ClickType;\n"
        "import net.minecraft.world.level.storage.DimensionDataStorage;\n"
        "import net.minecraft.client.renderer.LightTexture;\n"
        "import net.minecraft.client.renderer.state.CameraRenderState;\n"
        "ClickType.PICKUP; LightTexture.FULL_BRIGHT;",
        ("ContainerInput.PICKUP", "storage.SavedDataStorage", "net.minecraft.util.LightCoordsUtil", "state.level.CameraRenderState"),
        ("ClickType", "DimensionDataStorage", "LightTexture"),
    ),
    (
        "chunk record access and BlockPos construction",
        "ChunkPos plannerChunk; BlockPos position; plannerChunk.x; key.chunkPos.z; "
        "new ChunkPos(position); new ChunkPos(menu.tile.getBlockPos()); new ChunkPos(1, 2); new ChunkPos(packed);",
        ("plannerChunk.x()", "key.chunkPos.z()", "ChunkPos.containing(position)",
         "ChunkPos.containing(menu.tile.getBlockPos())", "new ChunkPos(1, 2)", "new ChunkPos(packed)"),
        ("plannerChunk.x;",),
    ),
    (
        "extracted GUI operations",
        "import net.minecraft.client.gui.GuiGraphics;\n"
        "guiGraphics.drawString(font, label, x, y, color, false); "
        "guiGraphics.renderItem(stack, x, y); guiGraphics.renderItemDecorations(font, stack, x, y); "
        "new net.minecraft.client.input.CharacterEvent(codePoint, modifiers);",
        ("GuiGraphicsExtractor", "guiGraphics.text(", "guiGraphics.item(", "guiGraphics.itemDecorations(", "CharacterEvent(codePoint)"),
        ("CharacterEvent(codePoint, modifiers)",),
    ),
    (
        "storage-free advancement triggers",
        "import net.minecraft.advancements.criterion.InventoryChangeTrigger;\n"
        "import net.minecraft.advancements.criterion.ItemPredicate;\n",
        ("advancements.triggers.InventoryChangeTrigger", "advancements.predicates.ItemPredicate"),
        ("advancements.criterion.",),
    ),
    (
        "swing animation",
        "player.swing(hand);",
        ("player.swing(hand, player.getItemInHand(hand).getInteractAnimation(), false);",),
        (),
    ),
    (
        "drop prediction",
        "player.drop(stack, false, true);",
        ("player.drop(stack, true, net.minecraft.util.Prediction.SERVER_ONLY);",),
        (),
    ),
    (
        "push reaction rename",
        "PushReaction.DESTROY; PushReaction.NORMAL;",
        ("PushReaction.POPPED", "PushReaction.PUSH_PULL"),
        ("PushReaction.DESTROY",),
    ),
    (
        "render relocations",
        "import com.mojang.blaze3d.vertex.VertexFormat;\nVertexFormat.Mode.QUADS; pose.mulPose(Axis.YP.rotationDegrees(90));",
        (
            "com.mojang.renderpearl.api.vertex.VertexFormat",
            "com.mojang.renderpearl.api.pipeline.PrimitiveTopology.QUADS",
            "pose.rotate(Axis.YP",
        ),
        ("mulPose(Axis",),
    ),
    (
        "baked quad material",
        "BakedQuad q; boolean s = quad.materialInfo().shade(); new BakedQuad.MaterialInfo(a, b, c, 0, true, 0, true);",
        (
            "BakedQuadCompat.shade(quad.materialInfo())",
            "BakedQuadCompat.materialInfo(a, b, c, 0, true, 0, true)",
        ),
        ("new BakedQuad.MaterialInfo(",),
    ),
    (
        "container screen getters",
        "IGuiArea.create(gui::getGuiLeft, gui::getGuiTop, gui::getXSize, gui::getYSize); screen.getGuiLeft();",
        ("gui::getLeftPos", "gui::getTopPos", "gui::getImageWidth", "gui::getImageHeight", "screen.getLeftPos()"),
        ("getGuiLeft", "getXSize"),
    ),
    (
        "GLFW keys",
        "import org.lwjgl.glfw.GLFW;\nif (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {}",
        ("InputConstants.KEY_RETURN", "SDLScancode.SDL_SCANCODE_KP_MINUS"),
        ("GLFW",),
    ),
    (
        "screen and uri",
        "int w = MC.screen == null ? 0 : MC.screen.width; Minecraft.getInstance().setScreen(s); "
        "Util.getPlatform().openUri(uri); event.scancode();",
        (
            "MC.gui.screen() == null",
            "MC.gui.screen().width",
            "Minecraft.getInstance().gui.setScreen(s)",
            "com.mojang.blaze3d.Blaze3D.openUri(uri)",
            "event.keycode()",
        ),
        ("getPlatform", "scancode"),
    ),
)


def fail(message: str) -> None:
    raise AssertionError(f"{TARGET}: {message}")


def check_transforms() -> None:
    for name, source, required, forbidden in TRANSFORM_CASES:
        generated = upgrade_263_symbols(source, minecraft="26.3", relative="buildcraft/Sample.java")
        for fragment in required:
            if fragment not in generated:
                fail(f"{name}: expected {fragment!r} in {generated!r}")
        for fragment in forbidden:
            if fragment in generated:
                fail(f"{name}: {fragment!r} survived in {generated!r}")
        if upgrade_263_symbols(source, minecraft="26.1.2", relative="buildcraft/Sample.java") != source:
            fail(f"{name}: 26.3 rewrite leaked into 26.1.2")

    try:
        upgrade_263_symbols(
            "import org.lwjgl.glfw.GLFW;\nint k = GLFW.GLFW_KEY_TAB;",
            minecraft="26.3",
            relative="buildcraft/Sample.java",
        )
    except ValueError:
        pass
    else:
        fail("an unmapped GLFW key must stop materialization")


def check_resource_migrations() -> None:
    sample = Path("sample.json")
    feature = _flatten_263_feature(
        {
            "type": "buildcraftenergy:oil",
            "config": {
                "plain": {"Name": "buildcraftenergy:oil"},
                "stateful": {"Name": "minecraft:furnace", "Properties": {"lit": "true"}},
            },
        },
        source=sample,
    )
    expected = {
        "type": "buildcraftenergy:oil",
        "plain": "buildcraftenergy:oil",
        "stateful": {"id": "minecraft:furnace", "properties": {"lit": "true"}},
    }
    if feature != expected:
        fail(f"feature migration produced {feature!r}")

    advancement = {
        "criteria": {
            "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": "buildcraftcore:wrench"}},
            "has_item": {"trigger": "minecraft:inventory_changed", "conditions": {"items": []}},
        }
    }
    if not _upgrade_263_criteria(advancement, source=sample):
        fail("recipe_unlocked criterion was not migrated")
    if advancement["criteria"]["has_the_recipe"]["conditions"] != {"recipes": "buildcraftcore:wrench"}:
        fail(f"recipe_unlocked migration produced {advancement!r}")
    if _upgrade_263_criteria(advancement, source=sample):
        fail("recipe_unlocked migration is not idempotent")

    # The 26.x fluid_container cover pass leaves grey fragments around GUI buckets.
    bucket = _dynamic_fluid_bucket_client_item_1_21_11("buildcraftenergy:oil_heat_1", minecraft="26.3")
    if "cover" in bucket:
        fail("26.3 heated buckets must not use the fluid_container cover mask")


def main() -> int:
    props = load_properties()
    expected = {
        "deps.minecraft": "26.3",
        "java.version": "25",
        "loader.version_range": "[12,)",
        "build.generation": "26.X",
        "minecraft.version_range": "[26.3,26.4)",
        "compat.jei.enabled": "true",
        "compat.jade.enabled": "true",
    }
    for key, value in expected.items():
        actual = props.get(f"target.{TARGET}.{key}")
        if actual != value:
            fail(f"target registry {key} is {actual!r}, expected {value!r}")

    effective = effective_source_files(target_layout(TARGET, props), props, "src/main/java")
    for relative in (
        "src/main/java/buildcraft/lib/compat/mc263/client/renderer/MultiBufferSource.java",
        "src/main/java/buildcraft/lib/compat/mc263/blaze3d/vertex/Tesselator.java",
        "src/main/java/buildcraft/lib/compat/mc263/client/BakedQuadCompat.java",
        "src/main/java/buildcraft/energy/BCEnergyWorldGen.java",
    ):
        if relative not in effective:
            fail(f"26.3 compatibility source is missing: {relative}")

    old_effective = effective_source_files(target_layout("26.1.2-neoforge", props), props, "src/main/java")
    if any("/compat/mc263/" in relative for relative in old_effective):
        fail("26.3 compatibility sources leak into 26.1.2")

    check_transforms()
    check_resource_migrations()
    print(f"{TARGET} target structure OK: {len(TRANSFORM_CASES)} transform cases")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
