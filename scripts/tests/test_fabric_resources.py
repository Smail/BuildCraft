#!/usr/bin/env python3
"""Fabric resource transforms: strict-NBT ingredients, bucket item definitions, and NeoForge isolation."""
from __future__ import annotations

import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))

from transforms import apply_text_transforms
from transforms.fabric import _parse_snbt, upgrade_fabric_symbols

RECIPE = "src/main/resources/data/buildcraftsilicon/recipe/assembly/gate/x.json"
FABRIC_RESOURCES = ROOT / "source-family-platforms/26.X/fabric/src/main/resources"
GATE_NBT = "{gate:{logic:0b,material:3b,modifier:1b}}"


def _recipe(ingredient: dict) -> str:
    return json.dumps({"type": "buildcraftsilicon:assembly", "ingredients": [ingredient]})


class SnbtParser(unittest.TestCase):
    def test_nested_compound_with_suffixes(self):
        self.assertEqual(_parse_snbt(GATE_NBT), {"gate": {"logic": 0, "material": 3, "modifier": 1}})

    def test_lists_strings_and_empty_compound(self):
        self.assertEqual(_parse_snbt('{a:[1,2],b:"x y",c:{}}'), {"a": [1, 2], "b": "x y", "c": {}})

    def test_malformed_input_raises(self):
        for bad in ("{a:}", "{a:1", "{a 1}", "{a:1} extra"):
            with self.subTest(bad=bad):
                with self.assertRaises((ValueError, IndexError, StopIteration)):
                    _parse_snbt(bad)


class StrictNbtIngredient(unittest.TestCase):
    def _transform(self, ingredient: dict) -> dict:
        out = upgrade_fabric_symbols(_recipe(ingredient), platform="fabric", relative=RECIPE)
        return json.loads(out)["ingredients"][0]

    def test_nbt_becomes_components_ingredient_for_both_key_spellings(self):
        for key in ("type", "neoforge:ingredient_type"):
            with self.subTest(key=key):
                result = self._transform({key: "buildcraftlib:strict_nbt", "count": 1, "item": "a:b", "nbt": GATE_NBT})
                self.assertEqual(result["fabric:type"], "fabric:components")
                self.assertEqual(result["base"], "a:b")
                self.assertEqual(result["components"]["minecraft:custom_data"]["gate"]["modifier"], 1)
                self.assertNotIn("count", result)

    def test_missing_nbt_becomes_plain_item_id(self):
        result = self._transform({"type": "buildcraftlib:strict_nbt", "count": 1, "item": "a:b"})
        self.assertEqual(result, "a:b")

    def test_other_platforms_and_plain_recipes_are_untouched(self):
        text = _recipe({"type": "buildcraftlib:strict_nbt", "item": "a:b", "nbt": GATE_NBT})
        self.assertEqual(upgrade_fabric_symbols(text, platform="neoforge", relative=RECIPE), text)
        plain = json.dumps({"type": "minecraft:crafting_shaped"})
        self.assertEqual(upgrade_fabric_symbols(plain, platform="fabric", relative=RECIPE), plain)


class BucketClientItems(unittest.TestCase):
    def _bucket_path(self) -> str:
        return "src/main/resources/assets/buildcraftenergy/items/oil/cool_bucket.json"

    def test_plain_model_definition_passes_through_on_26_3(self):
        text = (FABRIC_RESOURCES / "assets/buildcraftenergy/items/oil/cool_bucket.json").read_text(encoding="utf-8")
        out = apply_text_transforms(text, minecraft="26.3", relative=self._bucket_path(), native_source=False, platform="fabric")
        self.assertEqual(json.loads(out), json.loads(text))

    def test_neoforge_definition_is_still_rewritten(self):
        text = json.dumps({"model": {"type": "neoforge:fluid_container", "fluid": "x:y"}})
        out = apply_text_transforms(text, minecraft="26.3", relative=self._bucket_path(), native_source=False, platform="neoforge")
        self.assertEqual(json.loads(out)["model"]["type"], "neoforge:fluid_container")
        self.assertEqual(json.loads(out)["model"]["fluid"], "buildcraftenergy:oil")


class FabricResourceFiles(unittest.TestCase):
    def test_every_fabric_bucket_model_exists_and_has_particle(self):
        for item in (FABRIC_RESOURCES / "assets/buildcraftenergy/items").rglob("*.json"):
            model = json.loads(item.read_text(encoding="utf-8"))["model"]
            self.assertEqual(model["type"], "minecraft:model", item)
            namespace, path = model["model"].split(":")
            model_file = FABRIC_RESOURCES / f"assets/{namespace}/models/{path}.json"
            self.assertTrue(model_file.is_file(), f"{item}: missing {model_file}")
            self.assertIn("particle", json.loads(model_file.read_text(encoding="utf-8"))["textures"])

    def test_no_fabric_resource_references_neoforge_models(self):
        for path in FABRIC_RESOURCES.rglob("*.json"):
            self.assertNotIn("neoforge:", path.read_text(encoding="utf-8"), path)

    def test_fabric_tags_are_valid(self):
        for name in ("glass", "ingots/brick", "ingots/nether_brick"):
            data = json.loads((FABRIC_RESOURCES / f"data/c/tags/item/{name}.json").read_text(encoding="utf-8"))
            self.assertTrue(data["values"], name)


class SharedModels(unittest.TestCase):
    def test_energy_fluid_models_declare_particle_texture(self):
        root = ROOT / "source-shared/src/main/resources/assets/buildcraftenergy/models/fluids"
        files = list(root.rglob("*.json"))
        self.assertEqual(len(files), 30)
        for path in files:
            particle = json.loads(path.read_text(encoding="utf-8"))["textures"]["particle"]
            self.assertEqual(particle, f"buildcraftenergy:blocks/fluids/{path.parent.name}/{path.stem}_still")

    def test_architect_models_do_not_use_missing_orientable_slots(self):
        root = ROOT / "source-shared/src/main/resources/assets/buildcraftbuilders/models/block"
        for name in ("architect_on", "architect_off"):
            self.assertEqual(json.loads((root / f"{name}.json").read_text(encoding="utf-8"))["parent"], "block/cube")


if __name__ == "__main__":
    unittest.main()
