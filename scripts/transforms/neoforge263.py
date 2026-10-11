"""26.3-only Java relocations for the legacy BuildCraft storage implementation.

NeoForge removed the deprecated IItemHandler/IFluidHandler/IEnergyStorage family
in 26.3; do not generate shadow classes under the loader's own namespaces.
The BuildCraft-owned compatibility contracts remain INTERNAL and are exposed
through TransferInterop's native ResourceHandler/EnergyHandler boundaries.
"""
from __future__ import annotations

import re

from source_preprocessor import version_tuple

_RELOCATIONS = {
    "net.neoforged.neoforge.fluids.capability.IFluidHandlerItem": "buildcraft.lib.compat.neoforge263.fluids.IFluidHandlerItem",
    "net.neoforged.neoforge.fluids.capability.IFluidHandler": "buildcraft.lib.compat.neoforge263.fluids.IFluidHandler",
    "net.neoforged.neoforge.fluids.IFluidTank": "buildcraft.lib.compat.neoforge263.fluids.IFluidTank",
    "net.neoforged.neoforge.fluids.FluidUtil": "buildcraft.lib.compat.neoforge263.fluids.FluidUtil",
    "net.neoforged.neoforge.items.wrapper.InvWrapper": "buildcraft.lib.compat.neoforge263.items.wrapper.InvWrapper",
    "net.neoforged.neoforge.items.wrapper.CombinedInvWrapper": "buildcraft.lib.compat.neoforge263.items.wrapper.CombinedInvWrapper",
    "net.neoforged.neoforge.items.IItemHandlerModifiable": "buildcraft.lib.compat.neoforge263.items.IItemHandlerModifiable",
    "net.neoforged.neoforge.items.IItemHandler": "buildcraft.lib.compat.neoforge263.items.IItemHandler",
    "net.neoforged.neoforge.items.ItemHandlerCopySlot": "buildcraft.lib.compat.neoforge263.items.ItemHandlerCopySlot",
    "net.neoforged.neoforge.items.SlotItemHandler": "buildcraft.lib.compat.neoforge263.items.SlotItemHandler",
    "net.neoforged.neoforge.energy.IEnergyStorage": "buildcraft.lib.compat.neoforge263.energy.IEnergyStorage",
}

def relocate_removed_neoforge_apis(text: str, *, minecraft: str, relative: str) -> str:
    if not relative.endswith('.java') or version_tuple(minecraft) < version_tuple('26.3'):
        return text
    for old, new in _RELOCATIONS.items():
        text = text.replace(old, new)
    return text


def upgrade_minecraft_263_apis(text: str, *, minecraft: str, relative: str) -> str:
    """Precisely scoped 26.3 vanilla/loader migration after Java source selection.

    Do not apply these changes to 26.2: 26.3 removed public GUI accessors,
    changed tool/animation contracts, and replaced several enum values.
    """
    if not relative.endswith(".java") or version_tuple(minecraft) < version_tuple("26.3"):
        return text

    # 26.3 GUI getters, including those in ordinary components (not BC screens).
    for old, new in (("getGuiLeft()", "getLeftPos()"),
                     ("getGuiTop()", "getTopPos()"),
                     ("getXSize()", "getImageWidth()"),
                     ("getYSize()", "getImageHeight()")):
        text = text.replace(old, new)
        text = text.replace("::" + old[:-2], "::" + new[:-2])

    # FML 11 config types; COMMON's unsynchronized, both-sides replacement is LOCAL.
    text = text.replace("Type.COMMON", "Type.LOCAL")
    # Legacy motion-blocking heuristic and renamed piston reactions.
    text = text.replace(".blocksMotion()", ".isSolid()")
    for old, new in (("NORMAL", "PUSH_PULL"), ("DESTROY", "POPPED"),
                     ("BLOCK", "IMMOVEABLE"), ("IGNORE", "IGNORE_ENTITY"),
                     ("PUSH_ONLY", "PUSH")):
        text = re.sub(r"\bPushReaction\." + old + r"\b", "PushReaction." + new, text)

    # Newer PoseStack takes quaternions via rotate(), not mulPose().
    text = re.sub(r"\.mulPose\((Axis\.[A-Z]+\.rotationDegrees\()", r".rotate(\1", text)
    # Vec3i copy-constructor removed. Preserve all three coordinates, not a mutable alias.
    text = text.replace(".map(BlockPos::new)",
                        ".map(pos -> new BlockPos(pos.getX(), pos.getY(), pos.getZ()))")

    if "import org.lwjgl.glfw.GLFW;" in text:
        text = text.replace("import org.lwjgl.glfw.GLFW;",
                            "import com.mojang.blaze3d.platform.InputConstants;")
        keys = {"ENTER": "InputConstants.KEY_RETURN",
                "KP_ENTER": "InputConstants.KEY_NUMPADENTER",
                "EQUAL": "InputConstants.KEY_EQUALS",
                "KP_ADD": "InputConstants.KEY_ADD",
                # 26.3 uses SDL scancodes rather than GLFW key values. There is no
                # InputConstants.KEY_SUBTRACT: SDL names the numpad key KP_MINUS.
                "KP_SUBTRACT": "org.lwjgl.sdl.SDLScancode.SDL_SCANCODE_KP_MINUS"}
        text = re.sub(r"GLFW\.GLFW_KEY_([A-Z0-9_]+)",
                      lambda m: keys.get(m.group(1), "InputConstants.KEY_" + m.group(1)), text)

    # Tool subclasses were removed with the block transformer rewrite.
    for cls, tag in (("HoeItem", "HOES"), ("AxeItem", "AXES"),
                     ("ShovelItem", "SHOVELS")):
        if "import net.minecraft.world.item." + cls + ";" in text:
            text = text.replace("import net.minecraft.world.item." + cls + ";", "")
            text = re.sub(r"(\b[a-zA-Z_$][\w$]*)\.getItem\(\) instanceof " + cls + r"\b",
                          lambda m: m.group(1) + ".is(net.minecraft.tags.ItemTags." + tag + ")", text)

    # Modern player actions must supply the prediction/animation explicitly.
    text = re.sub(r"\bplayer\.drop\(([^(),]+),\s*(true|false),\s*(true|false)\)",
                  r"player.drop(\1, \2, net.minecraft.util.Prediction.SERVER_ONLY)", text)
    text = re.sub(r"\bplayer\.drop\(([^(),]+),\s*(true|false)\)",
                  r"player.drop(\1, \2, net.minecraft.util.Prediction.SERVER_ONLY)", text)
    text = re.sub(r"\bplayer\.swing\((\w+(?:\.getHand\(\))?)\)",
                  r"player.swing(\1, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true)", text)
    text = re.sub(r"\bplayer\.swingingArm\s*=\s*(\w+)\s*;",
                  r"player.swing(\1, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);", text)

    return text
