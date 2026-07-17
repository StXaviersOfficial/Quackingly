package com.quackcraft.quackingly.voice;

import com.quackcraft.quackingly.Quackingly;
import com.quackcraft.quackingly.companion.CompanionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Server-side orchestrator for the voice input pipeline.
 *
 * Flow:
 *   1. Player presses push-to-talk key (client) → VoiceInputStartPayload → onStartRecording()
 *   2. onStartRecording() tells QuackinglyVoiceChatPlugin to start a MicPacketCollector for this player
 *   3. While PTT held, SVC fires MicrophonePacketEvent → plugin decodes Opus → appends PCM to collector
 *   4. Player releases PTT → VoiceInputStopPayload → onStopRecording()
 *   5. onStopRecording() stops the collector, gets WAV bytes
 *   6. On a worker thread: GroqSTT.transcribe(wav) → text
 *   7. Forward text to CompanionManager.sendToCompanion() → LLM → reply (TTS plays on client)
 *
 * The STT + LLM call happens on a worker thread so we don't block the server tick.
 */
public final class VoiceInputHandler {

    private VoiceInputHandler() {}

    /** Called when client sends VoiceInputStartPayload (PTT pressed). */
    public static void onStartRecording(ServerPlayerEntity player) {
        try {
            // Check that SVC plugin is loaded (mic capture requires it)
            if (!isSvcAvailable()) {
                player.sendMessage(Text.literal(
                        "Simple Voice Chat is not installed — voice input unavailable.")
                        .formatted(Formatting.RED));
                return;
            }
            // Check config
            if (!com.quackcraft.quackingly.config.QuackinglyConfig.get().voiceInputEnabled) {
                // Silently ignore — client should have caught this, but be defensive
                return;
            }
            // Verify Quackingly is summoned (no point capturing audio if companion isn't there)
            CompanionManager.CompanionSession session = CompanionManager.getInstance().getSession(player);
            if (session == null || !session.isAlive()) {
                player.sendMessage(Text.literal("Summon Quackingly first (press K or type /quackingly).")
                        .formatted(Formatting.RED));
                return;
            }
            QuackinglyVoiceChatPlugin.startRecording(player.getUuid());
        } catch (Throwable t) {
            Quackingly.LOGGER.warn("[Quackingly] Failed to start voice recording", t);
        }
    }

    /** Called when client sends VoiceInputStopPayload (PTT released). */
    public static void onStopRecording(ServerPlayerEntity player) {
        final MinecraftServer server = player.getServer();
        if (server == null) return;

        // Stop the collector, get WAV bytes
        final byte[] wavBytes;
        try {
            wavBytes = QuackinglyVoiceChatPlugin.stopRecording(player.getUuid());
        } catch (Throwable t) {
            Quackingly.LOGGER.warn("[Quackingly] Failed to stop voice recording", t);
            return;
        }

        if (wavBytes == null || wavBytes.length == 0) {
            player.sendMessage(Text.literal("(no audio captured — did you hold the key long enough?)")
                    .formatted(Formatting.GRAY));
            return;
        }

        // Show the player their audio is being transcribed (subtle indicator)
        player.sendMessage(Text.literal("⟳ Transcribing...")
                .formatted(Formatting.ITALIC, Formatting.DARK_GRAY));

        // Transcribe on a worker thread (Groq STT call is blocking HTTP)
        final ServerPlayerEntity host = player;
        new Thread(() -> {
            try {
                String text = GroqSTT.transcribe(wavBytes);
                if (text == null || text.isBlank()) {
                    server.execute(() -> host.sendMessage(
                            Text.literal("(couldn't make out what you said)")
                                    .formatted(Formatting.GRAY)));
                    return;
                }
                // Show the player what was transcribed (so they know it worked)
                final String transcript = text;
                server.execute(() -> host.sendMessage(
                        Text.literal("[you] " + transcript).formatted(Formatting.ITALIC, Formatting.GRAY)));

                // Forward to companion — LLM call + reply + TTS happens here
                server.execute(() -> CompanionManager.getInstance().sendToCompanion(host, transcript));
            } catch (Exception e) {
                Quackingly.LOGGER.warn("[Quackingly] STT pipeline failed", e);
                server.execute(() -> host.sendMessage(
                        Text.literal("(STT failed: " + e.getMessage() + ")").formatted(Formatting.RED)));
            }
        }, "Quackingly-STT").start();
    }

    private static boolean isSvcAvailable() {
        try {
            // If the SVC plugin class loads without NoClassDefFoundError, SVC API is present
            Class.forName("de.maxhenkel.voicechat.api.VoicechatPlugin");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
