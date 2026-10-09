package buildcraft.lib.compat.minecraft.gui;

/** Regression check for recursive compatibility dispatch into retained BuildCraft panels. */
public final class FabricWidgetInputProbe {
    private FabricWidgetInputProbe() { }

    public static void main(String[] args) {
        final int[] calls = new int[2];
        BCWidgetInput panel = new BCWidgetInput() {
            public boolean mouseClicked(double x, double y, int button) {
                if (x != 12.5 || y != 7.5 || button != 1) {
                    throw new AssertionError("Mouse input changed during dispatch");
                }
                calls[0]++;
                return true;
            }

            public boolean keyPressed(int key, int scanCode, int modifiers) {
                if (key != 65 || scanCode != 30 || modifiers != 2) {
                    throw new AssertionError("Key input changed during dispatch");
                }
                calls[1]++;
                return true;
            }
        };
        if (!BCGuiInput.click(panel, 12.5, 7.5, 1)
            || !BCGuiInput.key(panel, 65, 30, 2)
            || calls[0] != 1 || calls[1] != 1) {
            throw new AssertionError("Panel must receive each event exactly once");
        }
        if (BCGuiInput.click(new Object(), 0, 0, 0)
            || BCGuiInput.key(new Object(), 0, 0, 0)) {
            throw new AssertionError("Unrelated targets must leave input unhandled");
        }
        System.out.println("Fabric widget input probe passed");
    }
}
