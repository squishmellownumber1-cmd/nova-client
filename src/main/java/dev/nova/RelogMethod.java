package dev.nova;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

/**
 * When you dig down to the chosen Y level: disconnect, wait, and rejoin the same server
 * (donutsmp.net if the current server can't be read). Re-arms once you climb above the level,
 * or steps the trigger deeper if "repeat every N blocks" is set.
 */
public final class RelogMethod {
    private enum State { WATCH, DISCONNECTING }

    private static State state = State.WATCH;
    private static boolean armed;
    private static int target = Integer.MIN_VALUE, lastLevel = Integer.MIN_VALUE, timer;
    private static ServerInfo server;

    public static void init() { ClientTickEvents.END_CLIENT_TICK.register(RelogMethod::tick); }

    private static void tick(MinecraftClient mc) {
        if (!Modules.RELOG.enabled) { state = State.WATCH; armed = false; target = Integer.MIN_VALUE; return; }
        if (Modules.relogLevel != lastLevel) { lastLevel = Modules.relogLevel; target = Modules.relogLevel; armed = false; }

        if (state == State.DISCONNECTING) {
            if (--timer <= 0 && mc.world == null && server != null) {
                try {
                    ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), mc,
                        ServerAddress.parse(server.address), server, false, null);
                } catch (Throwable t) { /* leave the player on the title/multiplayer screen */ }
                state = State.WATCH;
            }
            return;
        }

        if (mc.player == null || mc.world == null) return;
        double y = mc.player.getY();
        if (!armed && y > target + 2) armed = true;   // you must be above the level first
        if (!armed || y > target) return;

        ServerInfo info = mc.getCurrentServerEntry();
        if (info == null) info = new ServerInfo("DonutSMP", "donutsmp.net", ServerInfo.ServerType.OTHER);
        server = info;
        mc.player.sendMessage(Text.literal("[Nova] Relog method: reached y=" + (int) y + ", relogging..."), false);
        if (Modules.relogRepeat > 0) target = Math.max(-64, target - Modules.relogRepeat);
        armed = false;
        state = State.DISCONNECTING;
        timer = Modules.relogDelaySec * 20;
        mc.getNetworkHandler().getConnection().disconnect(Text.literal("Nova relog method"));
    }
}
