package dev.nova;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

/** Right Shift menu. Left-click toggles a module, right-click opens its options. */
public class NovaScreen extends Screen {
    private final Map<ButtonWidget, Modules.Module> buttons = new LinkedHashMap<>();
    private boolean rightWasDown;

    public NovaScreen() { super(Text.literal("Nova Client")); }

    private static Text label(Modules.Module m) {
        return Text.literal(m.name + ": " + (m.enabled ? "ON" : "OFF"));
    }

    @Override
    protected void init() {
        buttons.clear();
        int y = 40;
        for (Modules.Module m : Modules.ALL) {
            ButtonWidget b = ButtonWidget.builder(label(m), btn -> { m.enabled = !m.enabled; btn.setMessage(label(m)); })
                .dimensions(width / 2 - 110, y, 220, 20).build();
            addDrawableChild(b);
            buttons.put(b, m);
            y += 24;
        }
        y += 8;
        addDrawableChild(ButtonWidget.builder(Text.literal("Run Home Meta (sethome > rtp > home)"), b -> { close(); HomeMeta.start(); })
            .dimensions(width / 2 - 110, y, 220, 20).build());
        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
            .dimensions(width / 2 - 110, y, 220, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Left-click: toggle   Right-click: options"), width / 2, 26, 0xFFAAAAAA);

        // Right-click detection by polling the mouse button (independent of input-event signatures)
        boolean down = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (down && !rightWasDown) {
            for (Map.Entry<ButtonWidget, Modules.Module> e : buttons.entrySet()) {
                if (e.getKey().isMouseOver(mouseX, mouseY)) { client.setScreen(new ModuleScreen(e.getValue())); break; }
            }
        }
        rightWasDown = down;
    }
}
