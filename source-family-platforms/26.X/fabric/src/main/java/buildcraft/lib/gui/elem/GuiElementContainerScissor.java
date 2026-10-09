package buildcraft.lib.gui.elem;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import buildcraft.lib.gui.IGuiElement;
import buildcraft.lib.gui.json.BuildCraftJsonGui;
import buildcraft.lib.gui.pos.IGuiArea;
import buildcraft.lib.misc.GuiUtil;
import buildcraft.lib.misc.GuiUtil.AutoGlScissor;

/** A type of {@link GuiElementContainer2} that restricts the visible size of elements contained within. */
@Environment(EnvType.CLIENT)
public class GuiElementContainerScissor extends GuiElementContainer2 {

    public final IGuiArea area;

    public GuiElementContainerScissor(BuildCraftJsonGui gui, IGuiArea area) {
        super(gui);
        this.area = area;
    }

    public double getX() {
        return area.getX();
    }

    public double getY() {
        return area.getY();
    }

    public double getWidth() {
        return area.getWidth();
    }

    public double getHeight() {
        return area.getHeight();
    }

    public void drawBackground(GuiGraphicsExtractor guiGraphics, float partialTicks) {
        try (AutoGlScissor s = GuiUtil.scissor(guiGraphics, area)) {
            for (IGuiElement elem : getChildElements()) {
                elem.drawBackground(guiGraphics, partialTicks);
            }
        }
    }

    public void drawForeground(GuiGraphicsExtractor guiGraphics, float partialTicks) {
        try (AutoGlScissor s = GuiUtil.scissor(guiGraphics, area)) {
            for (IGuiElement elem : getChildElements()) {
                elem.drawForeground(guiGraphics, partialTicks);
            }
        }
    }
}
