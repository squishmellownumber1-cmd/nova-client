package dev.nova;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Options for one module. */
public class ModuleScreen extends Screen {
    private final Modules.Module module;

    public ModuleScreen(Modules.Module module) {
        super(Text.literal(module.name));
        this.module = module;
    }

    private static Text enabledLabel(Modules.Module m) { return Text.literal("Enabled: " + (m.enabled ? "ON" : "OFF")); }

    @Override
    protected void init() {
        int x = width / 2 - 130, y = 50;
        addDrawableChild(ButtonWidget.builder(enabledLabel(module), b -> { module.enabled = !module.enabled; b.setMessage(enabledLabel(module)); })
            .dimensions(width / 2 - 100, y, 200, 20).build());
        y += 30;

        for (Modules.Setting s : module.settings) {
            ButtonWidget value = ButtonWidget.builder(Text.literal(s.name + ": " + s.get.get()), b -> { }).dimensions(x + 64, y, 132, 20).build();
            value.active = false;
            addDrawableChild(value);
            int[] deltas = { -s.step * 10, -s.step, s.step, s.step * 10 };
            int[] xs = { x, x + 32, x + 198, x + 230 };
            for (int i = 0; i < 4; i++) {
                int d = deltas[i];
                addDrawableChild(ButtonWidget.builder(Text.literal((d > 0 ? "+" : "") + d), b -> {
                    s.add(d);
                    value.setMessage(Text.literal(s.name + ": " + s.get.get()));
                }).dimensions(xs[i], y, 30, 20).build());
            }
            y += 26;
        }

        if (module == Modules.BE_DEBUG) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Choose Blocks..."), b -> client.setScreen(new TracerScreen()))
                .dimensions(width / 2 - 100, y, 200, 20).build());
            y += 26;
        }
        y += 8;
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(new NovaScreen()))
            .dimensions(width / 2 - 100, y, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(module.desc), width / 2, 30, 0xFFAAAAAA);
    }
}
