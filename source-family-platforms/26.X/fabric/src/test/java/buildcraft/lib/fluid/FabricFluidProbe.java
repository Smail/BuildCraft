package buildcraft.lib.fluid;

import java.util.List;

import buildcraft.api.v2.fluid.FluidComponentPayload;
import buildcraft.lib.platform.storage.FabricTransferOperations;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluids;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/** Requires a real Fabric launch because FluidVariant caching is supplied by Fabric's fluid mixin. */
public final class FabricFluidProbe {
    private FabricFluidProbe() {}

    public static void main(String[] args) {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        FluidVariant water = FluidVariant.of(Fluids.WATER);
        int[] notifications = {0};
        FabricTank tank = new FabricTank("input", 1000, value -> true, () -> notifications[0]++);
        check(tank.nativeStorage().getSlot(0) == tank.nativeStorage().getSlot(0));
        try (Transaction outer = Transaction.openOuter()) {
            equal(0, tank.nativeStorage().insert(water, 80, outer));
            equal(8100, tank.nativeStorage().insert(water, 8110, outer));
            equal(100, tank.getFluid().amount());
        }
        equal(0, tank.getFluid().amount());
        equal(0, notifications[0]);
        try (Transaction outer = Transaction.openOuter()) {
            tank.nativeStorage().insert(water, 8100, outer);
            try (Transaction nested = outer.openNested()) {
                tank.nativeStorage().extract(water, 4050, nested);
                nested.commit();
            }
            equal(50, tank.getFluid().amount());
            equal(0, notifications[0]);
            outer.commit();
        }
        equal(50, tank.getFluid().amount());
        equal(1, notifications[0]);
        equal(100, FabricTransferOperations.transferFluid(100, true,
            (amount, transaction) -> tank.nativeStorage().insert(water, amount, transaction)));
        equal(50, tank.getFluid().amount());

        FabricTank dedicated = new FabricTank("dedicated", 1000, value -> true, () -> {});
        dedicated.setAccess(true, false);
        FabricTankManager manager = new FabricTankManager(List.of(tank, dedicated));
        check(manager.millibuckets().isFluidValid(0, new FabricFluidStack(water, 1)));
        check(manager.getSlot(0) == tank.nativeStorage().getSlot(0));
        try (Transaction outer = Transaction.openOuter()) {
            manager.insert(water, 8100, outer);
            outer.commit();
        }
        equal(100, dedicated.getFluid().amount());
        equal(50, tank.getFluid().amount());
        var persisted = manager.serializeNBT(registries);
        FabricTank reloadedInput = new FabricTank("input", 1000, value -> true, () -> {});
        FabricTank reloadedDedicated = new FabricTank("dedicated", 1000, value -> true, () -> {});
        FabricTankManager reloaded = new FabricTankManager(List.of(reloadedInput, reloadedDedicated));
        reloaded.deserializeNBT(registries, persisted);
        check(reloadedInput.getFluid().equals(tank.getFluid()));
        check(reloadedDedicated.getFluid().equals(dedicated.getFluid()));

        FabricTank first = new FabricTank("first", 100, value -> true, () -> {});
        FabricTank failing = new FabricTank("failing", 100, value -> { throw new IllegalStateException("Filter failed"); }, () -> {});
        FabricTankManager exceptional = new FabricTankManager(List.of(first, failing));
        try (Transaction outer = Transaction.openOuter()) {
            expectFailure(() -> exceptional.insert(water, 16_200, outer));
            equal(0, first.getFluid().amount());
            outer.commit();
        }
        equal(0, first.getFluid().amount());

        var patch = DataComponentPatch.builder().set(DataComponents.CUSTOM_NAME, Component.literal("Test fluid")).build();
        var componentFluid = FluidVariant.of(Fluids.WATER, patch);
        var api = FabricFluidVariants.toApi(componentFluid, registries);
        check(!api.components().isEmpty());
        check(FabricFluidVariants.toNative(api, registries).equals(componentFluid));
        FabricTank components = new FabricTank("components", 1000, value -> true, () -> {});
        components.setFluid(new FabricFluidStack(componentFluid, 250));
        var componentSave = components.serializeNBT(registries);
        FabricTank restored = new FabricTank("components", 1000, value -> true, () -> {});
        restored.deserializeNBT(registries, componentSave);
        check(restored.getFluid().equals(components.getFluid()));
        CompoundTag legacy = new CompoundTag();
        legacy.putString("FluidName", "minecraft:water");
        legacy.putInt("Amount", 500);
        CompoundTag custom = new CompoundTag();
        custom.putString("owner", "saved value");
        legacy.put("Tag", custom);
        restored.deserializeNBT(registries, legacy);
        equal(500, restored.getFluid().amount());
        var legacyCustom = restored.getFluid().variant().getComponents().get(DataComponents.CUSTOM_DATA);
        check(legacyCustom != null && legacyCustom.copyTag().equals(custom));
        restored.deserializeNBT(registries, componentSave);
        CompoundTag malformedManager = persisted.copy();
        malformedManager.put("input", new CompoundTag());
        malformedManager.putString("dedicated", "invalid compound");
        try {
            reloaded.deserializeNBT(registries, malformedManager);
            throw new AssertionError("Expected malformed manager data to fail");
        } catch (IllegalArgumentException expected) {
            check(reloadedInput.getFluid().equals(tank.getFluid()));
            check(reloadedDedicated.getFluid().equals(dedicated.getFluid()));
        }
        var corrupted = buildcraft.api.v2.fluid.FluidVariant.of(api.fluidId(), FluidComponentPayload.of(
            Identifier.fromNamespaceAndPath("buildcraftlib", "neoforge_fluid_components"), new byte[] {1, 2, 3}));
        try {
            FabricFluidVariants.toNative(corrupted, registries);
            throw new AssertionError("Expected malformed components to fail");
        } catch (IllegalArgumentException expected) {
            check(restored.getFluid().equals(components.getFluid()));
        }
        System.out.println("Fabric fluid native rollback, priority, component and persistence probes passed");
    }

    private static void equal(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Fluid storage invariant failed");
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Expected storage failure");
    }
}
