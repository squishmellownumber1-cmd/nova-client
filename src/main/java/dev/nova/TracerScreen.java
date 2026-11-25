package dev.nova;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Choose which block entities get tracers. */
public class TracerScreen extends Screen {
    public TracerScreen() { super(Text.literal("Tracer Blocks")); }

    private static Text label(String id) {
        return Text.literal(id.replace("_", " ") + ": " + (Modules.TRACER_TYPES.get(id) ? "ON" : "OFF"));
    }

    @Override
    protected void init() {
        int i = 0;
        for (String id : Modules.TRACER_TYPES.keySet()) {
            int col = i % 2, row = i / 2;
            addDrawableChild(ButtonWidget.builder(label(id), b -> {
                Modules.TRACER_TYPES.put(id, !Modules.TRACER_TYPES.get(id));
                b.setMessage(label(id));
            }).dimensions(width / 2 - 155 + col * 160, 40 + row * 24, 150, 20).build());
            i++;
        }
        int y = 40 + ((i + 1) / 2) * 24 + 10;
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(new NovaScreen()))
            .dimensions(width / 2 - 100, y, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFFFF);
    }
}
