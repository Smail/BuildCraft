"""Mechanical loader API relocations into BuildCraft's native Fabric adapters."""
from __future__ import annotations

import json
import re

_RELOCATIONS = (
    ("buildcraft.lib.compat.neoforge263.items.wrapper.CombinedInvWrapper", "buildcraft.lib.inventory.CombinedItemStorage"),
    ("net.neoforged.neoforge.items.wrapper.CombinedInvWrapper", "buildcraft.lib.inventory.CombinedItemStorage"),
    ("buildcraft.lib.compat.neoforge263.items.ItemHandlerCopySlot", "buildcraft.lib.gui.slot.ItemStorageCopySlot"),
    ("buildcraft.lib.compat.neoforge263.items.IItemHandlerModifiable", "buildcraft.lib.platform.storage.MutableItemStorage"),
    ("buildcraft.lib.compat.neoforge263.items.IItemHandler", "buildcraft.lib.platform.storage.ItemStorage"),
    ("buildcraft.lib.compat.neoforge263.items.ItemStackHandler", "buildcraft.lib.inventory.ItemStackStorage"),
    ("buildcraft.lib.compat.neoforge263.items.ItemHandlerHelper", "buildcraft.lib.inventory.ItemStorageHelper"),
    ("buildcraft.lib.compat.neoforge263.items.SlotItemHandler", "buildcraft.lib.gui.slot.SlotItemStorage"),
    ("buildcraft.lib.compat.neoforge263.energy.IEnergyStorage", "buildcraft.lib.platform.storage.EnergyStorage"),
    ("net.neoforged.neoforge.items.ItemHandlerHelper", "buildcraft.lib.inventory.ItemStorageHelper"),
    ("net.neoforged.neoforge.items.SlotItemHandler", "buildcraft.lib.gui.slot.SlotItemStorage"),
    ("net.neoforged.neoforge.fluids.FluidStack", "buildcraft.lib.fluid.BCFluidStack"),
    ("net.neoforged.neoforge.fluids.FluidType", "buildcraft.energy.fluid.BCFluidType"),
    ("net.neoforged.neoforge.fluids.FluidUtil", "buildcraft.lib.fluid.BCFluidUtil"),
    ("net.neoforged.neoforge.fluids.IFluidTank", "buildcraft.lib.fluid.BCFluidTank"),
    ("net.neoforged.neoforge.fluids.capability.IFluidHandlerItem", "buildcraft.lib.fluid.BCFluidHandlerItem"),
    ("net.neoforged.neoforge.fluids.capability.IFluidHandler", "buildcraft.lib.fluid.BCFluidHandler"),
    ("net.neoforged.neoforge.fluids.capability.templates.FluidTank", "buildcraft.lib.fluid.BCFluidTankStorage"),
    ("buildcraft.lib.compat.neoforge263.fluids.capability.IFluidHandlerItem", "buildcraft.lib.fluid.BCFluidHandlerItem"),
    ("buildcraft.lib.compat.neoforge263.fluids.capability.IFluidHandler", "buildcraft.lib.fluid.BCFluidHandler"),
    ("buildcraft.lib.compat.neoforge263.fluids.capability.templates.FluidTank", "buildcraft.lib.fluid.BCFluidTankStorage"),
    ("buildcraft.lib.compat.neoforge263.fluids.IFluidTank", "buildcraft.lib.fluid.BCFluidTank"),
    ("buildcraft.lib.compat.neoforge263.fluids.FluidUtil", "buildcraft.lib.fluid.BCFluidUtil"),
    ("net.neoforged.neoforge.items.IItemHandlerModifiable", "buildcraft.lib.platform.storage.MutableItemStorage"),
    ("net.neoforged.neoforge.items.IItemHandler", "buildcraft.lib.platform.storage.ItemStorage"),
    ("net.neoforged.neoforge.items.ItemStackHandler", "buildcraft.lib.inventory.ItemStackStorage"),
    ("net.neoforged.neoforge.energy.IEnergyStorage", "buildcraft.lib.platform.storage.EnergyStorage"),
    ("net.neoforged.neoforge.capabilities.BlockCapability", "buildcraft.lib.platform.capability.BCBlockCapability"),
    ("net.neoforged.neoforge.capabilities.EntityCapability", "buildcraft.lib.platform.capability.BCEntityCapability"),
    ("net.neoforged.neoforge.capabilities.Capabilities", "buildcraft.lib.platform.capability.BCCapabilities"),
    ("net.neoforged.neoforge.server.ServerLifecycleHooks", "buildcraft.lib.platform.server.PlatformServer"),
    ("net.neoforged.neoforge.model.data.ModelData", "buildcraft.lib.platform.client.BCModelData"),
    ("net.neoforged.neoforge.model.data.ModelProperty", "buildcraft.lib.platform.client.BCModelProperty"),
)


_SCREEN_FIELDS = (
    ("getLeftPos", "leftPos"),
    ("getTopPos", "topPos"),
    ("getImageWidth", "imageWidth"),
    ("getImageHeight", "imageHeight"),
    ("getMinecraft", "minecraft"),
)


_SNBT_TOKEN = re.compile(
    r'\s*(?:(?P<punct>[{}\[\],:])|"(?P<dq>(?:[^"\\]|\\.)*)"|\'(?P<sq>(?:[^\'\\]|\\.)*)\''
    r'|(?P<num>-?\d+(?:\.\d+)?[bBsSlLfFdD]?)(?![\w.+-])|(?P<word>[A-Za-z_][\w.+-]*))')


def _parse_snbt(text: str):
    """Parse the small SNBT subset used by legacy recipe ingredients into JSON-compatible values."""
    tokens = []
    pos = 0
    text = text.strip()
    while pos < len(text):
        match = _SNBT_TOKEN.match(text, pos)
        if match is None or match.end() == pos:
            raise ValueError(f"unsupported SNBT at {pos}: {text!r}")
        tokens.append(match)
        pos = match.end()
    index = 0

    def value():
        nonlocal index
        if index >= len(tokens):
            raise ValueError(f"unexpected end of SNBT: {text!r}")
        token = tokens[index]
        index += 1
        if token.group("punct") == "{":
            result = {}
            if tokens[index].group("punct") == "}":
                index += 1
                return result
            while True:
                key_token = tokens[index]
                index += 1
                key = next(g for g in (key_token.group("dq"), key_token.group("sq"),
                                       key_token.group("word"), key_token.group("num")) if g is not None)
                if tokens[index].group("punct") != ":":
                    raise ValueError(f"expected ':' in SNBT: {text!r}")
                index += 1
                result[key] = value()
                separator = tokens[index].group("punct")
                index += 1
                if separator == "}":
                    return result
                if separator != ",":
                    raise ValueError(f"expected ',' in SNBT: {text!r}")
        if token.group("punct") == "[":
            items = []
            if tokens[index].group("punct") == "]":
                index += 1
                return items
            while True:
                items.append(value())
                separator = tokens[index].group("punct")
                index += 1
                if separator == "]":
                    return items
                if separator != ",":
                    raise ValueError(f"expected ',' in SNBT: {text!r}")
        if token.group("dq") is not None:
            return token.group("dq")
        if token.group("sq") is not None:
            return token.group("sq")
        if token.group("num") is not None:
            number = token.group("num")
            if number[-1] in "fFdD" or "." in number:
                return float(number.rstrip("fFdD"))
            return int(number.rstrip("bBsSlL"))
        word = token.group("word")
        if word in ("true", "false"):
            return word == "true"
        return word

    result = value()
    if index != len(tokens):
        raise ValueError(f"trailing SNBT content: {text!r}")
    return result


def _fabric_recipe_ingredient(value):
    if isinstance(value, dict):
        if "buildcraftlib:strict_nbt" in (value.get("type"), value.get("neoforge:ingredient_type")) \
                and isinstance(value.get("item"), str):
            nbt = value.get("nbt")
            if not nbt:
                return value["item"]
            return {
                "fabric:type": "fabric:components",
                "base": value["item"],
                "components": {"minecraft:custom_data": _parse_snbt(nbt)},
            }
        return {key: _fabric_recipe_ingredient(child) for key, child in value.items()}
    if isinstance(value, list):
        return [_fabric_recipe_ingredient(child) for child in value]
    return value


def _upgrade_fabric_recipe_json(text: str) -> str:
    if "buildcraftlib:strict_nbt" not in text:
        return text
    # Fabric has no NeoForge ingredient types; strict-NBT ingredients become component ingredients.
    data = json.loads(text)
    return json.dumps(_fabric_recipe_ingredient(data), indent=2, ensure_ascii=False) + "\n"


def upgrade_fabric_symbols(text: str, *, platform: str, relative: str) -> str:
    if platform == "fabric" and relative.replace("\\", "/").endswith(".json") and "/recipe/" in relative.replace("\\", "/"):
        return _upgrade_fabric_recipe_json(text)
    if platform != "fabric" or not relative.endswith(".java"):
        return text
    for before, after in _RELOCATIONS:
        old_name, new_name = before.rsplit(".", 1)[1], after.rsplit(".", 1)[1]
        imported = re.search(r"\bimport\s+" + re.escape(before) + r"(?:[.;])", text)
        text = re.sub(re.escape(before) + r"(?![\w$])", after, text)
        if imported and old_name != new_name:
            text = re.sub(r"(?<![\w$.])" + old_name + r"\b", new_name, text)
    # Fluid stacks carry their BuildCraft fluid type directly; Fabric fluids have no FluidType.
    text = text.replace(".getFluid().getFluidType()", ".getFluidType()")
    text = text.replace("import net.neoforged.fml.ModList;\n", "import net.fabricmc.loader.api.FabricLoader;\n")
    text = re.sub(r"ModList\.get\(\)\.isLoaded\(", "FabricLoader.getInstance().isModLoaded(", text)
    text = text.replace(
        "ModList.get().getModContainerById(modId)\n"
        "            .map(container -> container.getModInfo().getDisplayName())",
        "FabricLoader.getInstance().getModContainer(modId)\n"
        "            .map(container -> container.getMetadata().getName())")
    # Vanilla Recipe.assemble takes only the input; NeoForge's 26.3 beta also passes the registry access.
    text = re.sub(r"\.assemble\((\w+), (\w+)\.registryAccess\(\)\)", r".assemble(\1)", text)
    # Block capability lookups go through CapUtil, which also consults native Fabric storages.
    text = re.sub(
        r"(\b[\w.()]+?)\.getCapability\(\s*([\w.]+CAP_\w+),\s*([^,()]+(?:\(\))?),\s*([\w.]+(?:\(\))?)\s*\)",
        r"buildcraft.lib.misc.CapUtil.getCapability(\1, \3, \2, \4)", text)
    text = text.replace("import net.neoforged.neoforge.common.Tags;\n",
                        "import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;\n")
    text = text.replace("Tags.Items.", "ConventionalItemTags.")
    text = text.replace(
        "((net.minecraft.server.level.ServerLevel) level).recipeAccess().recipeMap().byType(",
        "buildcraft.lib.compat.RecipeCompat.byType((net.minecraft.server.level.ServerLevel) level, ")
    # Vanilla exposes the bucket's fluid through a getter; the field is protected.
    text = text.replace("bucketItem.content", "bucketItem.getContent()")
    # Two NeoForge interfaces collapse into one adapter interface.
    text = text.replace("extends ItemStorage, ItemStorage,", "extends ItemStorage,")
    # A bare Fluid has no getFluidType() on Fabric; resolve it through the adapter.
    text = text.replace("toolTip.add(fluid.getFluidType().getDescription())",
                        "toolTip.add(buildcraft.energy.fluid.BCFluidType.of(fluid).getDescription())")
    # Vanilla key mappings match key objects; there is no isActiveAndMatches.
    text = text.replace(".isActiveAndMatches(", ".matches(")
    # NeoForge adds screen geometry getters; vanilla exposes the same values as fields (widened by the access widener).
    for getter, field in _SCREEN_FIELDS:
        text = re.sub(r"([A-Za-z_$][\w$.]*)::" + getter + r"\b", r"() -> \1." + field, text)
        text = re.sub(r"\." + getter + r"\(\)", "." + field, text)
    # Vanilla menu opening has no payload writer overload; Fabric uses an extended provider.
    # PlatformMenus itself must keep the vanilla call, otherwise open() would recurse into itself.
    if not relative.replace("\\", "/").endswith("/PlatformMenus.java"):
        text = re.sub(r"\b([A-Za-z_$][\w$]*)\.openMenu\(", r"buildcraft.lib.platform.registry.PlatformMenus.open(\1, ", text)
    return text
