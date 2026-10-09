package buildcraft.lib.compat;

import buildcraft.lib.compat.minecraft.render.BCRenderTypes;
import buildcraft.lib.compat.minecraft.gui.BCInputState;
import buildcraft.lib.compat.minecraft.gui.BCGuiTooltip;
import buildcraft.lib.compat.minecraft.gui.BCGuiInput;
import buildcraft.lib.compat.minecraft.gui.BCGraphics;
import java.util.List;
import java.util.function.Function;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import buildcraft.lib.compat.mc263.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import buildcraft.lib.compat.RenderCompat;

/** Runtime bridge between legacy BuildCraft rendering abstractions and the current client renderer. */
public final class RenderCompat {
    private static Identifier currentShaderTexture;
    private static final float[] currentShaderColor = { 1.0F, 1.0F, 1.0F, 1.0F };

    private RenderCompat() {
    }

    public static void setShaderTexture(int slot, Identifier texture) {
        if (slot == 0) {
            currentShaderTexture = texture;
        }
        // Deferred GUI paths use GuiGraphicsExtractor.blit instead of global texture binding, so the texture is only
        // stored for BuildCraft GUI helpers.
    }

    public static Identifier getShaderTexture() {
        return currentShaderTexture;
    }

    public static void setShaderColor(float red, float green, float blue, float alpha) {
        currentShaderColor[0] = red;
        currentShaderColor[1] = green;
        currentShaderColor[2] = blue;
        currentShaderColor[3] = alpha;
    }

    public static float[] getShaderColor() {
        return currentShaderColor.clone();
    }

    public static void clearColor(float red, float green, float blue, float alpha) {
        // No global state on 26.x, see enableDepthTest.
    }

    public static void enableDepthTest() {
        // RenderSystem has no global depth toggle on 26.x. Depth state lives in the render pipeline,
        // so there is nothing to enable here.
    }

    public static void disableBlend() {
        // No global state on 26.x, see enableDepthTest.
    }

    public static Function<Identifier, TextureAtlasSprite> blockSprites() {
        // Loaded atlases are resolved through AtlasManager. Use the atlas definition id (AtlasIds.BLOCKS), not
        // the backing texture location (TextureAtlas.LOCATION_BLOCKS), and provide missingno as the final fallback
        // instead of propagating null into MutableQuad.
        TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        TextureAtlasSprite missing = atlas.getSprite(MissingTextureAtlasSprite.getLocation());
        return location -> {
            TextureAtlasSprite sprite = atlas.getSprite(location);
            return sprite != null ? sprite : missing;
        };
    }

    public static DynamicTexture newDynamicTexture(int width, int height, boolean clear) {
        return new DynamicTexture("buildcraft_dynamic", width, height, clear);
    }

    /**
     * Native mouse events carry modifier state, while the shared BC8 statement widgets still expose the
     * legacy callback shape. GuiBC8 scopes this value around that callback so those widgets preserve the exact
     * Shift-click behaviour without polling removed Screen static helpers.
     */
    public static void setInputShiftDown(boolean shiftDown) {
        BCInputState.setShiftDown(shiftDown);
    }

    public static boolean hasInputShiftDown() {
        return BCInputState.shiftDown();
    }

    /** Exposes the 26.1.2 GUI extraction matrix to retained legacy draw helpers. */
    public static org.joml.Matrix3x2fStack pose(GuiGraphicsExtractor graphics) {
        return graphics.pose();
    }

    public static InputConstants.Key inputKey(int keyCode, int scanCode) {
        return BCGuiInput.key(keyCode, scanCode);
    }

    public static boolean mouseClicked(Object target, double mouseX, double mouseY, int button) {
        return BCGuiInput.click(target, mouseX, mouseY, button);
    }

    public static boolean mouseClicked(Object target, double mouseX, double mouseY, int button, boolean original) {
        boolean handled = mouseClicked(target, mouseX, mouseY, button);
        return original || handled;
    }

    public static boolean keyPressed(Object target, int keyCode, int scanCode, int modifiers) {
        return BCGuiInput.key(target, keyCode, scanCode, modifiers);
    }

    public static boolean mouseClicked(Object target, MouseButtonEvent event, boolean doubleClick) {
        return target instanceof GuiEventListener widget && widget.mouseClicked(event, doubleClick);
    }

    public static boolean keyPressed(Object target, KeyEvent event) {
        return target instanceof GuiEventListener widget && widget.keyPressed(event);
    }

    public static void renderTooltip(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int mouseX, int mouseY) {
        BCGuiTooltip.item(graphics, font, stack, mouseX, mouseY);
    }

    public static void blit(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int u, int v, int width, int height) {
        blit(graphics, texture, x, y, u, v, width, height, 256, 256);
    }

    public static RenderType solid() {
        return RenderTypes.solidMovingBlock();
    }

    public static RenderType cutout() {
        return RenderTypes.cutoutMovingBlock();
    }

    public static RenderType translucent() {
        return RenderTypes.translucentMovingBlock();
    }

    public static RenderType entityCutout(Identifier texture) {
        return BCRenderTypes.entityCutout(texture);
    }

    public static RenderType entityCutoutNoCull(Identifier texture) {
        return BCRenderTypes.entityCutoutNoCull(texture);
    }

    public static RenderType entityTranslucent(Identifier texture) {
        return BCRenderTypes.entityTranslucent(texture);
    }

    public static void enableBlend() {
        // No global state on 26.x, see enableDepthTest.
    }

    public static void defaultBlendFunc() {
        // No global state on 26.x, see enableDepthTest.
    }

    public static void disableDepthTest() {
        // No global state on 26.x, see enableDepthTest.
    }

    public static void disableScissor() {
        // GUI scissoring goes through GuiGraphicsExtractor on 26.x.
    }

    public static void enableScissor(int x, int y, int w, int h) {
        // GUI scissoring goes through GuiGraphicsExtractor on 26.x.
    }

    public static void setupFor3DItems() {
    }

    public static void setupForFlatItems() {
    }

    public static void renderComponentTooltip(GuiGraphicsExtractor graphics, Font font, List<Component> components, int mouseX, int mouseY) {
        BCGuiTooltip.components(graphics, font, components, mouseX, mouseY);
    }

    public static void renderTooltip(GuiGraphicsExtractor graphics, Font font, Component component, int mouseX, int mouseY) {
        BCGuiTooltip.text(graphics, font, component, mouseX, mouseY);
    }

    public static void blit(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int u, int v, int width, int height, int texWidth, int texHeight) {
        blit(graphics, texture, x, y, u, v, width, height, width, height, texWidth, texHeight);
    }

    public static void blit(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int u, int v, int width, int height,
        int sourceWidth, int sourceHeight, int texWidth, int texHeight) {
        BCGraphics.blit(graphics, texture, x, y, u, v, width, height, sourceWidth, sourceHeight, texWidth, texHeight);
    }

    /**
     * Retains source tinting while routing directly to the 26.1.2 extraction-stage GUI command.
     * This is intentionally a thin facade: {@link GuiGraphicsExtractor} owns the queued render state.
     */
    public static void blit(GuiGraphicsExtractor graphics, com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
        Identifier texture, int x, int y, float u, float v, int width, int height, int sourceWidth, int sourceHeight,
        int texWidth, int texHeight, int colour) {
        graphics.blit(pipeline, texture, x, y, u, v, width, height, sourceWidth, sourceHeight, texWidth, texHeight, colour);
    }

    public static void setShader(Object shader) {
    }

    public static void depthMask(boolean enabled) {
        // No global state on 26.x, see enableDepthTest.
    }

    public static void disableCull() {
        // No global state on 26.x, see enableDepthTest.
    }

    public static void setShaderFogStart(float value) {
    }

    public static void setShaderFogEnd(float value) {
    }

    public static Object profiler() {
        return null;
    }

}

