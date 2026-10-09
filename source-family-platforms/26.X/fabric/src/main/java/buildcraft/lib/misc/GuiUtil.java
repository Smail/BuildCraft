//? source if >=26.2
/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import buildcraft.lib.internal.debug.BCLog;
import buildcraft.lib.internal.core.render.ISprite;
import buildcraft.lib.client.render.fluid.FluidRenderer;
import buildcraft.lib.client.sprite.SpriteNineSliced;
import buildcraft.lib.client.sprite.SubSprite;
import buildcraft.lib.expression.api.IConstantNode;
import buildcraft.lib.platform.storage.FluidStorage;
import buildcraft.lib.gui.elem.ToolTip;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.gui.pos.IGuiArea;
import buildcraft.lib.gui.pos.IGuiPosition;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import buildcraft.lib.compat.minecraft.text.BCTextFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import buildcraft.lib.fluid.BCFluidStack;
import buildcraft.lib.fluid.FabricFluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Collections;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import buildcraft.lib.compat.RenderCompat;

@Environment(EnvType.CLIENT)
public final class GuiUtil {
    public static final IGuiArea AREA_WHOLE_SCREEN;
    private static final Minecraft MC = Minecraft.getInstance();

    static {
        AREA_WHOLE_SCREEN = IGuiArea.create(() -> 0, () -> 0, GuiUtil::getScreenWidth, GuiUtil::getScreenHeight);
    }

    private GuiUtil() {
    }

    public static int getScreenWidth() {
        return MC.gui.screen() == null ? MC.getWindow().getGuiScaledWidth() : MC.gui.screen().width;
    }

    public static int getScreenHeight() {
        return MC.gui.screen() == null ? MC.getWindow().getGuiScaledHeight() : MC.gui.screen().height;
    }

    public static IGuiArea moveRectangleToCentre(GuiRectangle area) {
        final double width = area.width;
        final double height = area.height;
        DoubleSupplier posX = () -> (AREA_WHOLE_SCREEN.getWidth() - width) / 2;
        DoubleSupplier posY = () -> (AREA_WHOLE_SCREEN.getHeight() - height) / 2;
        return IGuiArea.create(IGuiPosition.create(posX, posY), width, height);
    }

    public static IGuiArea moveAreaToCentre(IGuiArea area) {
        if (area instanceof GuiRectangle || area instanceof IConstantNode) {
            return moveRectangleToCentre(area.asImmutable());
        }
        DoubleSupplier posX = () -> (AREA_WHOLE_SCREEN.getWidth() - area.getWidth()) / 2;
        DoubleSupplier posY = () -> (AREA_WHOLE_SCREEN.getHeight() - area.getHeight()) / 2;
        return IGuiArea.create(posX, posY, area::getWidth, area::getHeight);
    }

    public static ToolTip createToolTip(Supplier<ItemStack> stackRef) {
        return new ToolTip() {
            public void refresh() {
                delegate().clear();
                ItemStack stack = stackRef.get();
                if (!stack.isEmpty()) {
                    delegate().addAll(getFormattedTooltip(stack));
                }
            }
        };
    }

    public static <D> void drawVerticallyAppending(IGuiPosition element, Iterable<? extends D> iterable,
        IVerticalAppendingDrawer<D> drawer, GuiGraphicsExtractor guiGraphics) {
        double x = element.getX();
        double y = element.getY();
        for (D drawable : iterable) {
            y += drawer.draw(drawable, guiGraphics, x, y);
        }
    }

    @FunctionalInterface
    public interface IVerticalAppendingDrawer<D> {
        double draw(D drawable, GuiGraphicsExtractor guiGraphics, double x, double y);
    }

    public static void drawItemStackAt(ItemStack stack, GuiGraphicsExtractor guiGraphics, int x, int y) {
        // GuiGraphicsExtractor owns item render state and lighting/depth setup.
        guiGraphics.fakeItem(stack, x, y);
        guiGraphics.itemDecorations(MC.font, stack, x, y);
    }

    /** Draws a vanilla-style tooltip and returns its occupied vertical space. */
    public static int drawHoveringText(GuiGraphicsExtractor guiGraphics, List<Component> textLines, int mouseX, int mouseY,
        int screenWidth, int screenHeight, int maxTextWidth, Font font) {
        if (textLines.isEmpty()) {
            return 0;
        }

        int tooltipTextWidth = 0;
        for (Component line : textLines) {
            tooltipTextWidth = Math.max(tooltipTextWidth, font.width(line));
        }
        if (maxTextWidth > 0) {
            tooltipTextWidth = Math.min(tooltipTextWidth, maxTextWidth);
        }

        int tooltipX = mouseX + 12;
        if (tooltipX + tooltipTextWidth + 4 > screenWidth) {
            tooltipX = mouseX - 16 - tooltipTextWidth;
        }
        tooltipX = Mth.clamp(tooltipX, 4, Math.max(4, screenWidth - tooltipTextWidth - 4));

        int tooltipHeight = 8;
        if (textLines.size() > 1) {
            tooltipHeight += (textLines.size() - 1) * 10 + 2;
        }
        int tooltipY = Mth.clamp(mouseY - 12, 4, Math.max(4, screenHeight - tooltipHeight - 6));

        final int background = 0xF0100010;
        final int borderStart = 0x505000FF;
        final int borderEnd = (borderStart & 0xFEFEFE) >> 1 | borderStart & 0xFF000000;

        guiGraphics.fillGradient(tooltipX - 3, tooltipY - 4, tooltipX + tooltipTextWidth + 3, tooltipY - 3, background, background);
        guiGraphics.fillGradient(tooltipX - 3, tooltipY + tooltipHeight + 3, tooltipX + tooltipTextWidth + 3, tooltipY + tooltipHeight + 4, background, background);
        guiGraphics.fillGradient(tooltipX - 3, tooltipY - 3, tooltipX + tooltipTextWidth + 3, tooltipY + tooltipHeight + 3, background, background);
        guiGraphics.fillGradient(tooltipX - 4, tooltipY - 3, tooltipX - 3, tooltipY + tooltipHeight + 3, background, background);
        guiGraphics.fillGradient(tooltipX + tooltipTextWidth + 3, tooltipY - 3, tooltipX + tooltipTextWidth + 4, tooltipY + tooltipHeight + 3, background, background);
        guiGraphics.fillGradient(tooltipX - 3, tooltipY - 2, tooltipX - 2, tooltipY + tooltipHeight + 2, borderStart, borderEnd);
        guiGraphics.fillGradient(tooltipX + tooltipTextWidth + 2, tooltipY - 2, tooltipX + tooltipTextWidth + 3, tooltipY + tooltipHeight + 2, borderStart, borderEnd);
        guiGraphics.fillGradient(tooltipX - 3, tooltipY - 3, tooltipX + tooltipTextWidth + 3, tooltipY - 2, borderStart, borderStart);
        guiGraphics.fillGradient(tooltipX - 3, tooltipY + tooltipHeight + 2, tooltipX + tooltipTextWidth + 3, tooltipY + tooltipHeight + 3, borderEnd, borderEnd);

        // Deferred GuiGraphicsExtractor rendering replaces legacy Z translation with an explicit stratum.
        guiGraphics.nextStratum();
        int y = tooltipY;
        for (int index = 0; index < textLines.size(); index++) {
            guiGraphics.text(font, textLines.get(index), tooltipX, y, -1, true);
            y += index == 0 && textLines.size() > 1 ? 12 : 10;
        }
        return tooltipHeight + 5;
    }

    public static void drawHorizontalLine(GuiGraphicsExtractor guiGraphics, int startX, int endX, int y, int color) {
        if (endX < startX) {
            int swap = startX;
            startX = endX;
            endX = swap;
        }
        guiGraphics.fill(startX, y, endX + 1, y + 1, color);
    }

    public static void drawVerticalLine(GuiGraphicsExtractor guiGraphics, int x, int startY, int endY, int color) {
        if (endY < startY) {
            int swap = startY;
            startY = endY;
            endY = swap;
        }
        guiGraphics.fill(x, startY + 1, x + 1, endY, color);
    }

    public static void drawRect(GuiGraphicsExtractor guiGraphics, IGuiArea area, int colour) {
        guiGraphics.fill((int) area.getX(), (int) area.getY(), (int) area.getEndX(), (int) area.getEndY(), colour);
    }

    public static void drawTexturedModalRect(GuiGraphicsExtractor guiGraphics, Identifier texture, double posX,
        double posY, double textureX, double textureY, double width, double height) {
        int x = Mth.floor(posX);
        int y = Mth.floor(posY);
        int u = Mth.floor(textureX);
        int v = Mth.floor(textureY);
        int w = Mth.floor(width);
        int h = Mth.floor(height);
        RenderCompat.blit(guiGraphics, texture, x, y, u, v, w, h);
    }

    /** Accepts storages of either the mutable gameplay carrier or the immutable native stack. */
    public static void drawFluid(GuiGraphicsExtractor guiGraphics, IGuiArea position, FluidStorage<?> tank) {
        Objects.requireNonNull(tank, "tank");
        if (tank.getTanks() <= 0) {
            return;
        }
        Object fluid = tank.getFluidInTank(0);
        if (fluid instanceof BCFluidStack gameplay) {
            drawFluid(guiGraphics, position, gameplay, tank.getTankCapacity(0));
        } else if (fluid instanceof FabricFluidStack nativeStack) {
            drawFluid(guiGraphics, position, nativeStack, tank.getTankCapacity(0));
        } else {
            throw new IllegalArgumentException("Unsupported fluid stack type: " + fluid);
        }
    }

    public static void drawFluid(GuiGraphicsExtractor guiGraphics, IGuiArea position, BCFluidStack fluid, int capacity) {
        if (fluid == null) {
            return;
        }
        drawFluid(guiGraphics, position, fluid.toNative(), capacity);
    }

    public static void drawFluid(GuiGraphicsExtractor guiGraphics, IGuiArea position, BCFluidStack fluid, int amount, int capacity) {
        if (fluid == null) {
            return;
        }
        drawFluid(guiGraphics, position, fluid.toNative(), amount, capacity);
    }

    public static void drawFluid(GuiGraphicsExtractor guiGraphics, IGuiArea position, FabricFluidStack fluid, int capacity) {
        if (fluid == null || fluid.isEmpty()) {
            return;
        }
        drawFluid(guiGraphics, position, fluid, fluid.getAmount(), capacity);
    }

    public static void drawFluid(GuiGraphicsExtractor guiGraphics, IGuiArea position, FabricFluidStack fluid, int amount, int capacity) {
        if (fluid == null || fluid.isEmpty() || amount <= 0 || capacity <= 0) {
            return;
        }
        Objects.requireNonNull(guiGraphics, "guiGraphics");
        Objects.requireNonNull(position, "position");
        double height = Math.min(amount, capacity) * position.getHeight() / capacity;
        double startX = position.getX();
        double endX = position.getEndX();
        double startY;
        double endY;
        if (FluidVariantAttributes.isLighterThanAir(fluid.variant())) {
            startY = position.getY() + height;
            endY = position.getY();
        } else {
            startY = position.getEndY();
            endY = startY - height;
        }
        FluidRenderer.drawFluidForGui(fluid, startX, startY, endX, endY, guiGraphics);
    }

    public static AutoGlScissor scissor(GuiGraphicsExtractor graphics, IGuiArea area) {
        Objects.requireNonNull(graphics, "graphics");
        Objects.requireNonNull(area, "area");
        GuiRectangle rect = area.asImmutable();
        graphics.enableScissor((int) Math.floor(rect.x), (int) Math.floor(rect.y),
            (int) Math.ceil(rect.getEndX()), (int) Math.ceil(rect.getEndY()));
        var captured = graphics.scissorStack.peek();
        return new AutoGlScissor() {
            private boolean closed;

            public void close() {
                if (closed) {
                    throw new IllegalStateException("Scissor scope already closed");
                }
                if (graphics.scissorStack.peek() != captured) {
                    throw new IllegalStateException("Closed scissor rectangles in the wrong order");
                }
                graphics.disableScissor();
                closed = true;
            }
        };
    }

    public static ISprite subRelative(ISprite sprite, double u, double v, double width, double height, double size) {
        return subRelative(sprite, u / size, v / size, width / size, height / size);
    }

    public static ISprite subAbsolute(ISprite sprite, double uMin, double vMin, double uMax, double vMax,
        double spriteSize) {
        return subAbsolute(sprite, uMin / spriteSize, vMin / spriteSize, uMax / spriteSize, vMax / spriteSize);
    }

    public static ISprite subRelative(ISprite sprite, double u, double v, double width, double height) {
        return subAbsolute(sprite, u, v, u + width, v + height);
    }

    public static ISprite subAbsolute(ISprite sprite, double uMin, double vMin, double uMax, double vMax) {
        if (uMin == 0 && vMin == 0 && uMax == 1 && vMax == 1) {
            return sprite;
        }
        return new SubSprite(sprite, uMin, vMin, uMax, vMax);
    }

    public static SpriteNineSliced slice(ISprite sprite, int uMin, int vMin, int uMax, int vMax, int textureSize) {
        return new SpriteNineSliced(sprite, uMin, vMin, uMax, vMax, textureSize);
    }

    public static SpriteNineSliced slice(ISprite sprite, double uMin, double vMin, double uMax, double vMax,
        double scale) {
        return new SpriteNineSliced(sprite, uMin, vMin, uMax, vMax, scale);
    }

    public interface AutoGlScissor extends AutoCloseable {
        void close();
    }

    public static List<Component> getFormattedTooltip(ItemStack stack) {
        List<Component> list = new java.util.ArrayList<>(getUnFormattedTooltip(stack));
        if (!list.isEmpty()) {
            Component first = list.get(0);
            list.set(0, first.copy().withStyle(style -> style.withColor(stack.getRarity().color())));
        }
        for (int i = 1; i < list.size(); i++) {
            Component line = list.get(i);
            list.set(i, line.copy().setStyle(BCTextFormat.GRAY.apply(line.getStyle())));
        }
        return list;
    }

    public static List<Component> getUnFormattedTooltip(ItemStack stack) {
        Item.TooltipContext context = MC.level == null ? Item.TooltipContext.EMPTY : Item.TooltipContext.of(MC.level);
        List<Component> list = stack.getTooltipLines(context, MC.player, getTooltipFlags());
        return list.isEmpty() ? Collections.singletonList(getStackDisplayName(stack)) : list;
    }

    public static Component getStackDisplayName(ItemStack stack) {
        Component name = stack.getDisplayName();
        if (name == null) {
            Item item = stack.getItem();
            String info = BuiltInRegistries.ITEM.getKey(item) + " " + item.getClass() + " (" + stack.getComponentsPatch() + ")";
            BCLog.logger.warn("[lib.guide] Found null display name! " + info);
            return Component.literal("!!NULL stack.getDisplayName(): " + info);
        }
        return name;
    }

    private static TooltipFlag getTooltipFlags() {
        return MC.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
    }

    @Deprecated
    public static WrappedTextData getWrappedTextData(String text, Font font, int maxWidth, boolean shadow, float scale) {
        List<FormattedCharSequence> lines = font.split(FormattedText.of(text), maxWidth);
        return new WrappedTextData(font, lines, shadow, scale, maxWidth, (int) (lines.size() * font.lineHeight * scale));
    }

    public static final class WrappedTextData {
        public final Font renderer;
        public final List<FormattedCharSequence> lines;
        public final float scale;
        public final boolean shadow;
        public final int width;
        public final int height;

        public WrappedTextData(Font renderer, List<FormattedCharSequence> lines, boolean shadow, float scale,
            int width, int height) {
            this.renderer = renderer;
            this.lines = lines;
            this.shadow = shadow;
            this.scale = scale;
            this.width = width;
            this.height = height;
        }

        public void drawAt(GuiGraphicsExtractor guiGraphics, int x, int y, int colour, boolean centered) {
            RenderCompat.pose(guiGraphics).pushMatrix();
            try {
            RenderCompat.pose(guiGraphics).scale(scale, scale);
            int drawX = Math.round(x / scale);
            int drawY = Math.round(y / scale);
            for (FormattedCharSequence line : lines) {
                int lineX = centered ? drawX - renderer.width(line) / 2 : drawX;
                guiGraphics.text(renderer, line, lineX, drawY, buildcraft.lib.client.sprite.GuiSpriteRender2612.opaqueRgb(colour), shadow);
                drawY += renderer.lineHeight;
            }
            } finally {
                RenderCompat.pose(guiGraphics).popMatrix();
            }
        }
    }
}
