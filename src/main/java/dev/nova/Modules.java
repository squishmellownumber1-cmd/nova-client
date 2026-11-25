package dev.nova;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Toggleable modules shown in the Right Shift menu. Right-click a module for its options. */
public final class Modules {
    // ---- tunable values (declared first so settings below can reference them) ----
    public static int baseScore = 6;
    public static int susBlocks = 40;
    public static int holeDepth = 12;
    public static int scanRadius = 12;
    public static int deepStorageMin = 3;
    public static int deepStorageMaxY = 40;
    public static int stashContainers = 12;
    public static int stashShulkers = 3;
    public static int hopperFarm = 6;
    public static int tracerRange = 128;
    public static int relogLevel = -5;
    public static int relogRepeat = 0;
    public static int relogDelaySec = 5;

    public static final class Setting {
        public final String name;
        public final int min, max, step;
        public final Supplier<Integer> get;
        public final Consumer<Integer> set;
        Setting(String name, int min, int max, int step, Supplier<Integer> get, Consumer<Integer> set) {
            this.name = name; this.min = min; this.max = max; this.step = step; this.get = get; this.set = set;
        }
        public void add(int delta) { set.accept(Math.max(min, Math.min(max, get.get() + delta))); }
    }

    public static final class Module {
        public final String name, desc;
        public boolean enabled;
        public final List<Setting> settings;
        Module(String name, String desc, boolean on, Setting... settings) {
            this.name = name; this.desc = desc; this.enabled = on; this.settings = List.of(settings);
        }
    }

    public static final Module BE_DEBUG = new Module("Block Entity Debug (Tracers)", "Tracer lines from your crosshair to chosen block entities", true,
        new Setting("Tracer range (blocks)", 16, 256, 16, () -> tracerRange, v -> tracerRange = v));
    public static final Module SPAWNER = new Module("Spawner Notifier", "Alerts for EVERY spawner, natural or not, with its mob type", true);
    public static final Module SUS_CHUNKS = new Module("Sus Chunk Finder", "Flags chunks with many non-natural blocks", true,
        new Setting("Non-natural blocks needed", 5, 500, 5, () -> susBlocks, v -> susBlocks = v));
    public static final Module DEEP = new Module("Deep Storage Finder", "Chests, ender chests, crafters, barrels, shulkers low underground", true,
        new Setting("Containers needed", 1, 30, 1, () -> deepStorageMin, v -> deepStorageMin = v),
        new Setting("Below Y", -60, 60, 5, () -> deepStorageMaxY, v -> deepStorageMaxY = v));
    public static final Module STASH = new Module("Stash Finder", "Chunks packed with containers / shulkers", true,
        new Setting("Containers needed", 4, 80, 2, () -> stashContainers, v -> stashContainers = v),
        new Setting("OR shulkers needed", 1, 30, 1, () -> stashShulkers, v -> stashShulkers = v));
    public static final Module BASE_FINDER = new Module("Base Finder", "Weighted score of player-made blocks per chunk", true,
        new Setting("Score needed", 1, 60, 1, () -> baseScore, v -> baseScore = v),
        new Setting("Scan radius (chunks, all modules)", 4, 32, 1, () -> scanRadius, v -> scanRadius = v));
    public static final Module FARMS = new Module("Beacon & Hopper Finder", "Beacons, conduits and hopper-heavy chunks (farms/bases)", true,
        new Setting("Hoppers needed", 2, 60, 1, () -> hopperFarm, v -> hopperFarm = v));
    public static final Module HOLES = new Module("Hole/Shaft Finder", "1x1 vertical shafts (heavy scan)", false,
        new Setting("Shaft depth", 6, 60, 2, () -> holeDepth, v -> holeDepth = v));
    public static final Module RELOG = new Module("Relog Method", "Leaves and rejoins the server when you dig down to a chosen Y level", false,
        new Setting("Relog at Y (20 to -64)", -64, 20, 1, () -> relogLevel, v -> relogLevel = v),
        new Setting("Repeat every N blocks deeper (0=off)", 0, 40, 1, () -> relogRepeat, v -> relogRepeat = v),
        new Setting("Rejoin delay (seconds)", 1, 30, 1, () -> relogDelaySec, v -> relogDelaySec = v));

    public static final List<Module> ALL = List.of(BE_DEBUG, SPAWNER, SUS_CHUNKS, DEEP, STASH, BASE_FINDER, FARMS, HOLES, RELOG);

    /** Which block entity types get a tracer (id path). Toggle in the Tracer Blocks screen. */
    public static final Map<String, Boolean> TRACER_TYPES = new LinkedHashMap<>();
    static {
        TRACER_TYPES.put("chest", true);
        TRACER_TYPES.put("ender_chest", true);
        TRACER_TYPES.put("barrel", true);
        TRACER_TYPES.put("trapped_chest", false);
        TRACER_TYPES.put("shulker_box", false);
        TRACER_TYPES.put("hopper", false);
        TRACER_TYPES.put("crafter", false);
        TRACER_TYPES.put("furnace", false);
        TRACER_TYPES.put("mob_spawner", true);
        TRACER_TYPES.put("beacon", false);
    }
}
