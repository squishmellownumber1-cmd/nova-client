package dev.nova;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class NovaClient implements ClientModInitializer {
    public static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("nova", "main"));
    private static KeyBinding menuKey;
    private static final Path DIR = FabricLoader.getInstance().getGameDir().resolve("nova");

    @Override
    public void onInitializeClient() {
        HomeMeta.init();
        AutoScan.init();
        TracerRenderer.init();
        RelogMethod.init();
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.nova.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.wasPressed()) if (mc.currentScreen == null) mc.setScreen(new NovaScreen());
        });
        ClientCommandRegistrationCallback.EVENT.register((d, reg) -> d.register(
            ClientCommandManager.literal("nova")
                .executes(c -> help(c))
                .then(ClientCommandManager.literal("scan")
                    .executes(c -> scan(c, 16, false))
                    .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1, 32))
                        .executes(c -> scan(c, IntegerArgumentType.getInteger(c, "radius"), false))))
                .then(ClientCommandManager.literal("sus")
                    .executes(c -> scan(c, 16, true))
                    .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1, 32))
                        .executes(c -> scan(c, IntegerArgumentType.getInteger(c, "radius"), true))))
                .then(ClientCommandManager.literal("base").executes(c -> {
                    AutoScan.Found f = AutoScan.nearest(MinecraftClient.getInstance());
                    if (f == null) { say(c, "No bases found yet."); return 0; }
                    say(c, "Nearest: x=" + f.x() + " z=" + f.z() + "  " + f.reason()); return 1; }))
                .then(ClientCommandManager.literal("homemeta").executes(c -> { HomeMeta.start(); return 1; }))
                .then(ClientCommandManager.literal("be").executes(NovaClient::blockEntityDebug))
                .then(ClientCommandManager.literal("relog")
                    .then(ClientCommandManager.literal("snapshot").executes(c -> snapshot(c)))
                    .then(ClientCommandManager.literal("compare").executes(c -> compare(c))))
        ));
    }

    private static void say(CommandContext<FabricClientCommandSource> c, String s) {
        c.getSource().sendFeedback(Text.literal("[Nova] " + s));
    }

    private static int help(CommandContext<FabricClientCommandSource> c) {
        say(c, "/nova scan [radius]   - rank loaded chunks by block-entity score");
        say(c, "/nova sus [radius]    - scan + count non-natural blocks (obsidian, concrete, ...)");
        say(c, "/nova be              - block entity breakdown for your chunk + all loaded chunks");
        say(c, "Right Shift = module menu. /nova base = nearest flagged base. Results saved to nova/bases.txt");
        say(c, "/nova homemeta        - sethome, rtp, then home (or press H)");
        say(c, "/nova relog snapshot  - save current chunk scores, then relog / move, then:");
        say(c, "/nova relog compare   - list chunks whose scores changed or appeared");
        return 1;
    }

    private static int scan(CommandContext<FabricClientCommandSource> c, int radius, boolean sus) {
        List<Scanner.Hit> hits = Scanner.scan(MinecraftClient.getInstance(), radius, sus);
        if (hits.isEmpty()) { say(c, "Nothing found in " + radius + " chunk radius."); return 0; }
        say(c, hits.size() + " chunks flagged (top 10):");
        for (int i = 0; i < Math.min(10, hits.size()); i++) {
            Scanner.Hit h = hits.get(i);
            say(c, String.format("#%d  x=%d z=%d  y %d..%d  score=%.1f sus=%d  %s",
                i + 1, h.blockX(), h.blockZ(), h.minY(), h.maxY(), h.beScore(), h.sus(), h.beCounts()));
        }
        write("last-scan.txt", hits.stream().map(h -> h.blockX() + "," + h.blockZ() + "," + h.total()).toList());
        return hits.size();
    }

    private static int blockEntityDebug(CommandContext<FabricClientCommandSource> c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return 0;
        WorldChunk here = mc.world.getChunkManager().getWorldChunk(mc.player.getChunkPos().x, mc.player.getChunkPos().z);
        if (here != null) say(c, "Your chunk: " + Scanner.analyze(here, false).beCounts());
        Map<String, Integer> total = new TreeMap<>();
        for (Scanner.Hit h : Scanner.scan(mc, 32, false)) h.beCounts().forEach((k, v) -> total.merge(k, v, Integer::sum));
        say(c, "All loaded: " + total);
        return 1;
    }

    private static int snapshot(CommandContext<FabricClientCommandSource> c) {
        List<Scanner.Hit> hits = Scanner.scan(MinecraftClient.getInstance(), 32, true);
        write("snapshot.txt", hits.stream().map(h -> h.pos().x + "," + h.pos().z + "," + h.total()).toList());
        say(c, "Saved " + hits.size() + " chunks.");
        return hits.size();
    }

    private static int compare(CommandContext<FabricClientCommandSource> c) {
        Map<String, Double> old = new HashMap<>();
        try {
            for (String l : Files.readAllLines(DIR.resolve("snapshot.txt"))) {
                String[] p = l.split(",");
                old.put(p[0] + "," + p[1], Double.parseDouble(p[2]));
            }
        } catch (Exception e) { say(c, "No snapshot found. Run /nova relog snapshot first."); return 0; }
        int n = 0;
        for (Scanner.Hit h : Scanner.scan(MinecraftClient.getInstance(), 32, true)) {
            Double before = old.get(h.pos().x + "," + h.pos().z);
            if (before == null || Math.abs(before - h.total()) > 0.01) {
                say(c, String.format("%s x=%d z=%d  %s -> %.1f", before == null ? "NEW" : "CHANGED",
                    h.blockX(), h.blockZ(), before == null ? "-" : String.format("%.1f", before), h.total()));
                if (++n >= 15) break;
            }
        }
        if (n == 0) say(c, "No differences.");
        return n;
    }

    private static void write(String name, List<String> lines) {
        try { Files.createDirectories(DIR); Files.write(DIR.resolve(name), lines); } catch (Exception ignored) { }
    }
}
