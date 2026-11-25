package dev.nova;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.function.Predicate;

/** Passive analysis of chunks the server has already sent to the client. No packets are sent. */
public final class Scanner {
    /** Block entity weights: player-placed storage/utility is a strong base signal. */
    public static final Map<String, Double> BE_WEIGHTS = Map.ofEntries(
        Map.entry("chest", 1.0), Map.entry("trapped_chest", 1.0), Map.entry("barrel", 1.0),
        Map.entry("shulker_box", 3.0), Map.entry("ender_chest", 2.0),
        Map.entry("furnace", 1.0), Map.entry("blast_furnace", 1.5), Map.entry("smoker", 1.5),
        Map.entry("hopper", 1.5), Map.entry("dispenser", 1.0), Map.entry("dropper", 1.0),
        Map.entry("beacon", 5.0), Map.entry("conduit", 3.0), Map.entry("crafter", 2.0),
        Map.entry("brewing_stand", 1.0), Map.entry("enchanting_table", 1.0),
        Map.entry("bed", 1.0), Map.entry("sign", 0.5), Map.entry("hanging_sign", 0.5),
        Map.entry("lectern", 0.5), Map.entry("jukebox", 0.5), Map.entry("comparator", 1.0),
        Map.entry("daylight_detector", 1.0), Map.entry("mob_spawner", 1.0)
    );

    /** Block id fragments that rarely generate naturally. Tune to taste. */
    public static final String[] SUS_FRAGMENTS = {
        "obsidian", "concrete", "bookshelf", "beacon", "respawn_anchor", "observer",
        "piston", "redstone_lamp", "sea_lantern", "glass", "shulker_box", "ender_chest",
        "netherite_block", "diamond_block", "emerald_block", "gold_block", "iron_block"
    };

    private static final Map<Block, Boolean> SUS_CACHE = new HashMap<>();

    public static final Predicate<BlockState> SUS = state -> SUS_CACHE.computeIfAbsent(state.getBlock(), b -> {
        String path = Registries.BLOCK.getId(b).getPath();
        for (String f : SUS_FRAGMENTS) if (path.contains(f)) return true;
        return false;
    });

    public record Hit(ChunkPos pos, double beScore, int sus, Map<String, Integer> beCounts, int minY, int maxY) {
        public double total() { return beScore + sus * 0.25; }
        public int blockX() { return pos.getStartX() + 8; }
        public int blockZ() { return pos.getStartZ() + 8; }
    }

    public static List<Hit> scan(MinecraftClient mc, int radius, boolean includeSus) {
        List<Hit> out = new ArrayList<>();
        if (mc.world == null || mc.player == null) return out;
        ChunkPos center = mc.player.getChunkPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                WorldChunk ch = mc.world.getChunkManager().getWorldChunk(center.x + dx, center.z + dz);
                if (ch == null) continue;
                Hit h = analyze(ch, includeSus);
                if (h.total() > 0) out.add(h);
            }
        }
        out.sort(Comparator.comparingDouble(Hit::total).reversed());
        return out;
    }

    public static Hit analyze(WorldChunk ch, boolean includeSus) {
        Map<String, Integer> counts = new TreeMap<>();
        double score = 0;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockEntity be : ch.getBlockEntities().values()) {
            String id = Registries.BLOCK_ENTITY_TYPE.getId(be.getType()).getPath();
            counts.merge(id, 1, Integer::sum);
            Double w = BE_WEIGHTS.get(id);
            if (w != null) {
                score += w;
                minY = Math.min(minY, be.getPos().getY());
                maxY = Math.max(maxY, be.getPos().getY());
            }
        }
        int sus = 0;
        if (includeSus) {
            ChunkSection[] sections = ch.getSectionArray();
            for (int i = 0; i < sections.length; i++) {
                ChunkSection sec = sections[i];
                if (sec == null || sec.isEmpty() || !sec.hasAny(SUS)) continue;
                int baseY = ch.getBottomY() + i * 16;
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                    if (SUS.test(sec.getBlockState(x, y, z))) {
                        sus++;
                        minY = Math.min(minY, baseY + y);
                        maxY = Math.max(maxY, baseY + y);
                    }
                }
            }
        }
        return new Hit(ch.getPos(), score, sus, counts, minY, maxY);
    }
}
