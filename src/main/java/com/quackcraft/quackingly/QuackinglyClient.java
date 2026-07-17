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
 * - Push-to-talk keybind (-)
 * - Client-side packet receivers:
 *     CompanionReplyPayload          → triggers TTS playback
 *     OpenConfirmationScreenPayload  → opens ConfirmSummonScreen (from /quackingly)
 */
public class QuackinglyClient implements ClientModInitializer {
    public static final String CATEGORY = "key.quackingly.category";

    private static KeyBinding openMenuKey;
    private static KeyBinding summonKey;
    private static KeyBinding talkKey;

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

    private void onEndTick(MinecraftClient client) {
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
        }
    }

    public static KeyBinding getTalkKey() { return talkKey; }
}
