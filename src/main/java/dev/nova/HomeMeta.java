package dev.nova;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/** Keybind: /sethome 1 -> /rtp -> wait for the teleport -> /home 1. Default key: H. */
public final class HomeMeta {
    private enum State { IDLE, WAIT_SET, WAIT_RTP, WAIT_HOME }

    public static int rtpTimeoutTicks = 400;   // give up waiting for rtp after 20s
    public static int homeDelayTicks = 40;     // pause after landing before /home

    private static KeyBinding key;
    private static State state = State.IDLE;
    private static int timer;
    private static Vec3d startPos;

    public static void init() {
        key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.nova.homemeta", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H,
            NovaClient.CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(HomeMeta::tick);
    }

    public static void start() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || state != State.IDLE) return;
        mc.player.networkHandler.sendChatCommand("sethome 1");
        state = State.WAIT_SET;
        timer = 20;
        msg("Home set, rtp in 1s...");
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null) { state = State.IDLE; return; }
        while (key.wasPressed()) start();
        switch (state) {
            case WAIT_SET -> {
                if (--timer <= 0) {
                    startPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                    mc.player.networkHandler.sendChatCommand("rtp");
                    state = State.WAIT_RTP;
                    timer = rtpTimeoutTicks;
                }
            }
            case WAIT_RTP -> {
                boolean moved = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).squaredDistanceTo(startPos) > 100 * 100;
                if (moved || --timer <= 0) {
                    state = State.WAIT_HOME;
                    timer = homeDelayTicks;
                    msg(moved ? "RTP done, going home..." : "RTP timed out, going home...");
                }
            }
            case WAIT_HOME -> {
                if (--timer <= 0) {
                    mc.player.networkHandler.sendChatCommand("home 1");
                    state = State.IDLE;
                }
            }
            default -> { }
        }
    }

    private static void msg(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Nova] " + s), false);
    }
}
