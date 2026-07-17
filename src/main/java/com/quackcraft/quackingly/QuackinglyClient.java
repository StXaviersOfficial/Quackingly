package com.quackcraft.quackingly;

import com.quackcraft.quackingly.client.network.ClientCompanionPackets;
import com.quackcraft.quackingly.client.screen.ConfirmSummonScreen;
import com.quackcraft.quackingly.client.screen.WorldPickerScreen;
import com.quackcraft.quackingly.config.QuackinglyConfig;
import com.quackcraft.quackingly.voice.QuackinglyVoiceChatPlugin;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint. Handles:
 * - Title-screen keybind (Q) -> WorldPickerScreen
 * - In-game keybind (K) -> summon/despawn Quackingly
 * - Push-to-talk keybind (-) — optional manual override
 * - Mute-toggle keybind (P) — mute/unmute always-on listening
 * - Client-side packet receivers:
 *     CompanionReplyPayload          → triggers TTS playback
 *     OpenConfirmationScreenPayload  → opens ConfirmSummonScreen (from /quackingly)
 *
 * Always-on voice input (like Dr Donut's Verity) is the default — no key needed.
 * The server's SilenceWatcher detects sentence boundaries automatically via
 * SVC's VAD + a 600ms silence gap.
 */
public class QuackinglyClient implements ClientModInitializer {
    public static final String CATEGORY = "key.quackingly.category";

    private static KeyBinding openMenuKey;
    private static KeyBinding summonKey;
    private static KeyBinding talkKey;
    private static KeyBinding muteKey;

    // Auto-summon flag: set when world is launched from "Play with Quackingly" button
    private static boolean autoSummonOnJoin = false;
    private static String autoSummonMode = "normal";

    @Override
    public void onInitializeClient() {
        QuackinglyConfig.load();

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.quackingly.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Q,
                CATEGORY));

        summonKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.quackingly.summon",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                CATEGORY));

        talkKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.quackingly.talk",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_MINUS,
                CATEGORY));

        muteKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.quackingly.mute",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);

        // Client receiver: Quackingly's reply → triggers TTS playback
        ClientPlayNetworking.registerGlobalReceiver(
                ClientCompanionPackets.CompanionReplyPayload.ID,
                (payload, context) -> {
                    String reply = payload.text();
                    context.client().execute(() ->
                            com.quackcraft.quackingly.voice.ClientVoiceController.onCompanionReply(reply));
                });

        // Client receiver: server tells us to open the confirmation popup (from /quackingly)
        ClientPlayNetworking.registerGlobalReceiver(
                ClientCompanionPackets.OpenConfirmationScreenPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.player != null) {
                            mc.setScreen(new ConfirmSummonScreen());
                        }
                    });
                });

        // Make sure the SVC plugin class loads early so SVC picks it up
        try {
            Class.forName(QuackinglyVoiceChatPlugin.class.getName());
        } catch (ClassNotFoundException e) {
            Quackingly.LOGGER.warn("Voice chat plugin class not found", e);
        }
    }

    public static void setAutoSummonOnJoin(boolean val, String mode) {
        autoSummonOnJoin = val;
        autoSummonMode = mode;
    }

    private void onEndTick(MinecraftClient client) {
        // Auto-summon Quackingly when world was launched from "Play with Quackingly"
        if (autoSummonOnJoin && client.world != null && client.player != null && client.currentScreen == null) {
            autoSummonOnJoin = false; // only fire once
            // Send summon with mode packet after a short delay (let world finish loading)
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException ie) { return; }
                client.execute(() -> {
                    ClientCompanionPackets.sendSummonWithMode(autoSummonMode);
                    if (client.player != null) {
                        client.player.sendMessage(net.minecraft.text.Text.literal(
                                "Auto-summoning Quackingly (" + autoSummonMode + " mode)...")
                                .formatted(net.minecraft.util.Formatting.AQUA));
                    }
                });
            }, "Quackingly-AutoSummon").start();
        }

        // Title-screen keybind (Q) → WorldPickerScreen
        boolean onTitle = client.currentScreen == null
                || client.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen;
        if (onTitle && client.world == null) {
            while (openMenuKey.wasPressed()) {
                client.setScreen(new WorldPickerScreen(client.currentScreen));
            }
        }

        // In-game keybinds
        if (client.world != null && client.player != null && client.currentScreen == null) {
            while (summonKey.wasPressed()) {
                ClientCompanionPackets.sendToggleSummon();
            }
            while (talkKey.wasPressed()) {
                com.quackcraft.quackingly.voice.ClientVoiceController.togglePushToTalk();
            }
            while (muteKey.wasPressed()) {
                ClientCompanionPackets.sendToggleMute();
            }
        }
    }

    public static KeyBinding getTalkKey() { return talkKey; }
    public static KeyBinding getMuteKey() { return muteKey; }
}
