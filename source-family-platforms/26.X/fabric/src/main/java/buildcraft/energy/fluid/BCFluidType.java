package buildcraft.energy.fluid;

import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import buildcraft.lib.fluid.BCFluidStack;

/** Native Fabric fluid physical and visual properties, independent of client classes. */
public final class BCFluidType {
    public static final int BUCKET_VOLUME = 1000;
    private final int density, viscosity, temperature, tint;
    private final Identifier still, flow;
    private final String descriptionId;

    public static BCFluidType of(net.minecraft.world.level.material.Fluid fluid) {
        Objects.requireNonNull(fluid, "fluid");
        if (fluid instanceof buildcraft.lib.fluid.BCFluid oil) return oil.getFluidType();
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
        boolean lava = fluid == net.minecraft.world.level.material.Fluids.LAVA
            || fluid == net.minecraft.world.level.material.Fluids.FLOWING_LAVA;
        return new BCFluidType(lava ? 3000 : 1000, lava ? 6000 : 1000, lava ? 1300 : 300,
            Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + id.getPath() + "_still"),
            Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + id.getPath() + "_flow"), 0xFFFFFFFF);
    }

    public BCFluidType(int density, int viscosity, int temperature, Identifier still, Identifier flow, int tint) {
        if (viscosity <= 0 || temperature < 0) throw new IllegalArgumentException("Invalid fluid physical properties");
        this.density = density;
        this.viscosity = viscosity;
        this.temperature = temperature;
        this.still = Objects.requireNonNull(still, "still texture");
        this.flow = Objects.requireNonNull(flow, "flow texture");
        this.tint = tint;
        String path = still.getPath();
        if (path.startsWith("blocks/fluids/")) {
            String[] parts = path.substring("blocks/fluids/".length()).split("/");
            // Texture "<fluid>/<heat>_still" maps to the lang key "fluid_type.<namespace>.<fluid>_<heat>".
            StringBuilder key = new StringBuilder("fluid_type.").append(still.getNamespace()).append('.').append(parts[0]);
            if (parts.length > 1) {
                key.append('_').append(parts[1].replace("_still", "").replace("_flow", ""));
            }
            descriptionId = key.toString();
        } else {
            descriptionId = "block." + still.getNamespace() + "." + path.replace("block/", "").replace("_still", "");
        }
    }
    public int getDensity() { return density; }
    public int getViscosity() { return viscosity; }
    public int getTemperature() { return temperature; }
    public boolean isLighterThanAir() { return density < 0; }
    public Identifier getStillTextureLocation() { return still; }
    public Identifier getFlowTextureLocation() { return flow; }
    public int getFluidTintColor() { return tint; }
    public String getDescriptionId() { return descriptionId; }
    public int getLightLevel() { return temperature > 1000 ? 15 : 0; }
    public int getLightLevel(BCFluidStack stack) { return getLightLevel(); }
    public boolean canExtinguish() { return temperature <= 350; }
    public boolean canHydrate() { return descriptionId.equals("block.minecraft.water"); }
    public boolean canDrownIn() { return density > 0; }
    public boolean canSwim() { return false; }
    public double getMotionScale() { return 0.014; }
    public Component getDescription() {
        return Component.translatable(descriptionId);
    }
    public Component getDescription(BCFluidStack stack) {
        Objects.requireNonNull(stack, "stack");
        return Component.translatable(descriptionId);
    }
}
