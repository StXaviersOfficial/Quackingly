package com.quackcraft.quackingly.voice;

import com.quackcraft.quackingly.Quackingly;
import com.quackcraft.quackingly.client.network.ClientCompanionPackets;
import com.quackcraft.quackingly.config.QuackinglyConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import javax.sound.sampled.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Client-side voice pipeline controller.
 *
 * Voice INPUT (push-to-talk):
 *   PTT key down  → send VoiceInputStartPayload to server
 *                   (server starts collecting mic packets from SVC's MicrophonePacketEvent)
 *   PTT key up    → send VoiceInputStopPayload to server
 *                   (server stops collecting, transcribes via Groq Whisper STT,
 *                    forwards text to CompanionManager → LLM → reply)
 *
 * The actual audio capture happens SERVER-SIDE via the SVC plugin. The client
 * just signals when to start/stop. This is how Verity does it — the mic audio
 * is captured by SVC's transport on the server, decoded there, and transcribed
 * there. No audio bytes travel over our custom packet channel.
 *
 * Voice OUTPUT (TTS):
 *   When server sends a CompanionReplyPayload, we synthesise TTS audio via
 *   the configured provider (Fish Audio default, OpenAI fallback) and play it.
 *   32-entry TTS cache avoids re-synthesising identical replies.
 */
public final class ClientVoiceController {

    private ClientVoiceController() {}

    private static boolean pttActive = false;
    private static final ConcurrentMap<String, byte[]> ttsCache = new ConcurrentHashMap<>();
    private static final int TTS_CACHE_MAX = 32;

    // ===== Voice input (push-to-talk) =====

    public static void togglePushToTalk() {
        if (!QuackinglyConfig.get().voiceInputEnabled) {
            MinecraftClient.getInstance().player.sendMessage(
                    Text.literal("Voice input is disabled. Enable it in Mod Menu → Quackingly → Voice.")
                            .formatted(Formatting.RED));
            return;
        }
        if (pttActive) releasePtt();
        else pressPtt();
    }

    public static void pressPtt() {
        try {
            ClientCompanionPackets.sendVoiceInputStart();
            pttActive = true;
            // Show a subtle indicator that we're recording
            MinecraftClient.getInstance().player.sendMessage(
                    Text.literal("● Recording... (release to send)").formatted(Formatting.DARK_RED, Formatting.BOLD));
        } catch (Throwable t) {
            Quackingly.LOGGER.warn("Failed to start PTT", t);
        }
    }

    public static void releasePtt() {
        if (!pttActive) return;
        pttActive = false;
        try {
            ClientCompanionPackets.sendVoiceInputStop();
        } catch (Throwable t) {
            Quackingly.LOGGER.warn("Failed to release PTT", t);
        }
    }

    public static boolean isPttActive() { return pttActive; }

    // ===== Voice output (TTS playback) =====

    /**
     * Called when the server sends us Quackingly's reply text.
     * Synthesise TTS audio via the configured provider and play it back.
     */
    public static void onCompanionReply(String replyText) {
        if (replyText == null || replyText.isBlank()) return;

        TTSProvider provider = TTSProvider.fromConfig();
        if (!provider.isReady()) {
            Quackingly.LOGGER.debug("[Quackingly] TTS provider '{}' not ready (no API key set); skipping voice.",
                    provider.getProviderName());
            return;
        }

        new Thread(() -> {
            try {
                byte[] mp3 = ttsCache.computeIfAbsent(replyText, t -> {
                    try {
                        return provider.synthesise(t);
                    } catch (Exception e) {
                        Quackingly.LOGGER.warn("TTS synthesis failed (provider={}): {}",
                                provider.getProviderName(), e.getMessage());
                        return new byte[0];
                    }
                });
                if (mp3 != null && mp3.length > 0) {
                    playMp3AtCompanion(mp3);
                }
                if (ttsCache.size() > TTS_CACHE_MAX) {
                    ttsCache.clear();
                }
            } catch (Throwable t) {
                Quackingly.LOGGER.warn("TTS playback pipeline failed", t);
            }
        }, "Quackingly-TTS").start();
    }

    /**
     * Play back an MP3 byte[] via Java Sound API (SourceDataLine).
     * Works on Pojav/Mojo too — no MC SoundSystem dependency.
     */
    public static void playMp3AtCompanion(byte[] mp3Bytes) {
        MinecraftClient.getInstance().execute(() -> {
            Path tmp = null;
            try {
                tmp = Files.createTempFile("quackingly_tts_", ".mp3");
                Files.write(tmp, mp3Bytes);

                AudioInputStream in = AudioSystem.getAudioInputStream(tmp.toFile());
                AudioFormat baseFormat = in.getFormat();
                AudioFormat decodedFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        baseFormat.getSampleRate(),
                        16,
                        baseFormat.getChannels(),
                        baseFormat.getChannels() * 2,
                        baseFormat.getSampleRate(),
                        false);
                AudioInputStream din = AudioSystem.getAudioInputStream(decodedFormat, in);

                SourceDataLine line = AudioSystem.getSourceDataLine(decodedFormat);
                line.open(decodedFormat);
                line.start();
                if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    gain.setValue(-3.0f);
                }

                byte[] buf = new byte[4096];
                int n;
                while ((n = din.read(buf)) != -1) line.write(buf, 0, n);
                line.drain();
                line.stop();
                line.close();
                din.close();
                in.close();
            } catch (Throwable t) {
                Quackingly.LOGGER.warn("TTS playback failed", t);
            } finally {
                if (tmp != null) try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
            }
        });
    }
}
