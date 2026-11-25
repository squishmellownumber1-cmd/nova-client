package dev.nova;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws tracers on the 2D HUD layer: block positions are projected to screen space from the
 * player's view, then a dotted line is drawn from the crosshair to each target.
 */
public final class TracerRenderer {
    private record Target(Vec3d pos, String type) {}
    private static volatile List<Target> targets = List.of();
    private static int tick;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(TracerRenderer::update);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.of("nova", "tracers"), TracerRenderer::render);
    }

    private static void update(MinecraftClient mc) {
        if (mc.world == null || mc.player == null || !Modules.BE_DEBUG.enabled) { targets = List.of(); return; }
        if (++tick % 10 != 0) return;
        List<Target> out = new ArrayList<>();
        ChunkPos c = mc.player.getChunkPos();
        int r = Math.min(12, Modules.tracerRange / 16 + 1);
        double max2 = (double) Modules.tracerRange * Modules.tracerRange;
        Vec3d eye = mc.player.getEyePos();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            WorldChunk ch = mc.world.getChunkManager().getWorldChunk(c.x + dx, c.z + dz);
            if (ch == null) continue;
            for (BlockEntity be : ch.getBlockEntities().values()) {
                String id = Registries.BLOCK_ENTITY_TYPE.getId(be.getType()).getPath();
                if (!Boolean.TRUE.equals(Modules.TRACER_TYPES.get(id))) continue;
                Vec3d p = Vec3d.ofCenter(be.getPos());
                if (p.squaredDistanceTo(eye) <= max2) out.add(new Target(p, id));
            }
        }
        targets = out;
    }

    private static int color(String type) {
        return switch (type) {
            case "chest" -> 0xFFFFA500;
            case "trapped_chest" -> 0xFFFF5555;
            case "ender_chest" -> 0xFFB44CFF;
            case "barrel" -> 0xFFB07A3A;
            case "shulker_box" -> 0xFFFF77FF;
            case "mob_spawner" -> 0xFF55FF55;
            case "beacon" -> 0xFF55FFFF;
            case "hopper" -> 0xFFAAAAAA;
            default -> 0xFFFFFF55;
        };
    }

    private static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null || !Modules.BE_DEBUG.enabled) return;
        List<Target> list = targets;
        if (list.isEmpty()) return;

        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        double cx = w / 2.0, cy = h / 2.0;
        Vec3d eye = mc.player.getEyePos();
        double yaw = Math.toRadians(mc.player.getYaw()), pitch = Math.toRadians(mc.player.getPitch());
        Vec3d f = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3d up = right.crossProduct(f);
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double aspect = (double) w / h;

        for (Target t : list) {
            Vec3d d = t.pos().subtract(eye);
            double xc = d.dotProduct(right), yc = d.dotProduct(up), zc = d.dotProduct(f);
            double px, py;
            boolean behind = zc <= 0.05;
            if (behind) {
                double len = Math.max(1e-6, Math.hypot(xc, yc));
                px = cx + (xc / len) * w;
                py = cy - (yc / len) * w;
            } else {
                px = cx + (xc / (zc * tanHalf * aspect)) * (w / 2.0);
                py = cy - (yc / (zc * tanHalf)) * (h / 2.0);
            }
            int col = color(t.type());
            line(ctx, cx, cy, px, py, w, h, col);
            if (!behind && px > 4 && px < w - 40 && py > 4 && py < h - 12) {
                ctx.fill((int) px - 2, (int) py - 2, (int) px + 3, (int) py + 3, col);
                ctx.drawTextWithShadow(mc.textRenderer, Text.literal(t.type().replace("_", " ") + " " + (int) d.length() + "m"), (int) px + 5, (int) py - 4, col);
            }
        }
    }

    private static void line(DrawContext ctx, double x0, double y0, double x1, double y1, int w, int h, int col) {
        double dx = x1 - x0, dy = y1 - y0;
        double len = Math.hypot(dx, dy);
        int steps = (int) Math.min(len / 2.0, 800);
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            int x = (int) (x0 + dx * t), y = (int) (y0 + dy * t);
            if (x < 0 || y < 0 || x >= w || y >= h) continue;
            ctx.fill(x, y, x + 1, y + 1, col);
        }
    }
}
