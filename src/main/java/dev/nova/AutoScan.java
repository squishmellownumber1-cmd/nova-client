package dev.nova;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/** Background scanner: checks each newly loaded chunk once and alerts in chat. */
public final class AutoScan {
    public record Found(int x, int z, String reason) {}
    public static final List<Found> FOUND = new ArrayList<>();

    private static final Set<Long> DONE = new HashSet<>();
    private static final ArrayDeque<ChunkPos> QUEUE = new ArrayDeque<>();
    private static ClientWorld lastWorld;
    private static int tick;
    private static final Path FILE = FabricLoader.getInstance().getGameDir().resolve("nova").resolve("bases.txt");

    private static final String[] NATURAL_SPAWNER_BLOCKS = {
        "mossy_cobblestone", "stone_brick_stairs", "cobweb", "nether_brick_fence", "chain", "cobblestone"
    };

    public static void init() { ClientTickEvents.END_CLIENT_TICK.register(AutoScan::tick); }

    private static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) { lastWorld = null; return; }
        if (mc.world != lastWorld) { lastWorld = mc.world; DONE.clear(); QUEUE.clear(); FOUND.clear(); SEEN_SPAWNERS.clear(); }

        if (++tick % 60 == 0) refill(mc);
        for (int i = 0; i < 3 && !QUEUE.isEmpty(); i++) {
            ChunkPos p = QUEUE.poll();
            WorldChunk ch = mc.world.getChunkManager().getWorldChunk(p.x, p.z);
            if (ch != null) check(mc, ch);
        }
    }

    private static void refill(MinecraftClient mc) {
        boolean any = false;
        for (Modules.Module m : Modules.ALL) if (m != Modules.BE_DEBUG && m.enabled) any = true;
        if (!any) return;
        ChunkPos c = mc.player.getChunkPos();
        int r = Modules.scanRadius;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            ChunkPos p = new ChunkPos(c.x + dx, c.z + dz);
            if (DONE.contains(p.toLong())) continue;
            if (mc.world.getChunkManager().getWorldChunk(p.x, p.z) == null) continue;
            DONE.add(p.toLong());
            QUEUE.add(p);
        }
    }

    private static void check(MinecraftClient mc, WorldChunk ch) {
        boolean needSus = Modules.SUS_CHUNKS.enabled;
        Scanner.Hit h = Scanner.analyze(ch, needSus);
        int x = h.blockX(), z = h.blockZ();

        if (Modules.BASE_FINDER.enabled && h.beScore() >= Modules.baseScore)
            alert(mc, x, z, String.format("Base Finder score %.1f %s", h.beScore(), h.beCounts()));
        if (needSus && h.sus() >= Modules.susBlocks)
            alert(mc, x, z, "Sus chunk: " + h.sus() + " non-natural blocks");
        if (Modules.DEEP.enabled || Modules.STASH.enabled || Modules.FARMS.enabled) {
            int deep = 0, containers = 0, shulkers = 0, hoppers = 0;
            for (BlockEntity be : ch.getBlockEntities().values()) {
                String id = Registries.BLOCK_ENTITY_TYPE.getId(be.getType()).getPath();
                boolean store = id.equals("chest") || id.equals("trapped_chest") || id.equals("ender_chest")
                    || id.equals("barrel") || id.equals("crafter") || id.equals("shulker_box")
                    || id.equals("dispenser") || id.equals("dropper");
                if (store) {
                    containers++;
                    if (be.getPos().getY() < Modules.deepStorageMaxY) deep++;
                }
                if (id.equals("shulker_box")) shulkers++;
                if (id.equals("hopper")) hoppers++;
                if (Modules.FARMS.enabled && (id.equals("beacon") || id.equals("conduit")))
                    alert(mc, be.getPos().getX(), be.getPos().getZ(), id + " at y=" + be.getPos().getY());
            }
            if (Modules.DEEP.enabled && deep >= Modules.deepStorageMin)
                alert(mc, x, z, "Deep storage: " + deep + " containers below Y=" + Modules.deepStorageMaxY);
            if (Modules.STASH.enabled && (containers >= Modules.stashContainers || shulkers >= Modules.stashShulkers))
                alert(mc, x, z, "STASH: " + containers + " containers, " + shulkers + " shulkers");
            if (Modules.FARMS.enabled && hoppers >= Modules.hopperFarm)
                alert(mc, x, z, "Hopper-heavy chunk (farm/base): " + hoppers + " hoppers");
        }
        if (Modules.SPAWNER.enabled) checkSpawners(mc, ch);
        if (Modules.HOLES.enabled) checkHoles(mc, ch);
    }

    private static final Set<BlockPos> SEEN_SPAWNERS = new HashSet<>();

    private static String spawnerType(MinecraftClient mc, BlockEntity be) {
        try {
            var nbt = be.createNbtWithIdentifyingData(mc.world.getRegistryManager());
            return nbt.getCompound("SpawnData").flatMap(sd -> sd.getCompound("entity"))
                .flatMap(e -> e.getString("id")).orElse("unknown").replace("minecraft:", "");
        } catch (Throwable t) { return "unknown"; }
    }

    private static void checkSpawners(MinecraftClient mc, WorldChunk ch) {
        for (BlockEntity be : ch.getBlockEntities().values()) {
            if (!Registries.BLOCK_ENTITY_TYPE.getId(be.getType()).getPath().equals("mob_spawner")) continue;
            BlockPos p = be.getPos().toImmutable();
            if (!SEEN_SPAWNERS.add(p)) continue;
            boolean natural = false;
            BlockPos.Mutable m = new BlockPos.Mutable();
            outer:
            for (int dx = -4; dx <= 4; dx++) for (int dy = -3; dy <= 3; dy++) for (int dz = -4; dz <= 4; dz++) {
                m.set(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                String id = Registries.BLOCK.getId(mc.world.getBlockState(m).getBlock()).getPath();
                for (String n : NATURAL_SPAWNER_BLOCKS) if (id.equals(n)) { natural = true; break outer; }
            }
            String type = spawnerType(mc, be);
            alert(mc, p.getX(), p.getZ(), "SPAWNER [" + type + "] y=" + p.getY() + (natural ? " (looks natural)" : " (UNNATURAL - likely a base)"));
        }
    }

    private static boolean solid(BlockState s) { return !s.isAir() && s.getFluidState().isEmpty(); }

    private static void checkHoles(MinecraftClient mc, WorldChunk ch) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        int bx = ch.getPos().getStartX(), bz = ch.getPos().getStartZ();
        int lo = Math.max(ch.getBottomY(), -60), hi = 60;
        for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) {
            int run = 0;
            for (int y = hi; y >= lo; y--) {
                boolean shaft = ch.getBlockState(m.set(bx + x, y, bz + z)).isAir()
                    && solid(ch.getBlockState(m.set(bx + x + 1, y, bz + z)))
                    && solid(ch.getBlockState(m.set(bx + x - 1, y, bz + z)))
                    && solid(ch.getBlockState(m.set(bx + x, y, bz + z + 1)))
                    && solid(ch.getBlockState(m.set(bx + x, y, bz + z - 1)));
                if (shaft) {
                    if (++run >= Modules.holeDepth) { alert(mc, bx + x, bz + z, "1x1 shaft, " + run + "+ deep, top y~" + (y + run)); return; }
                } else run = 0;
            }
        }
    }

    private static void alert(MinecraftClient mc, int x, int z, String reason) {
        FOUND.add(new Found(x, z, reason));
        msg(mc, "x=" + x + " z=" + z + "  " + reason);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, x + "," + z + "," + reason + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }

    public static Found nearest(MinecraftClient mc) {
        Found best = null; double bd = Double.MAX_VALUE;
        for (Found f : FOUND) {
            double d = Math.pow(f.x() - mc.player.getX(), 2) + Math.pow(f.z() - mc.player.getZ(), 2);
            if (d < bd) { bd = d; best = f; }
        }
        return best;
    }

    private static void msg(MinecraftClient mc, String s) { mc.player.sendMessage(Text.literal("[Nova] " + s), false); }
}
