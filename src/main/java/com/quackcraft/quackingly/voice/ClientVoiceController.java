package com.quackcraft.quackingly.voice;

import com.quackcraft.quackingly.Quackingly;
import com.quackcraft.quackingly.client.network.ClientCompanionPackets;
import com.quackcraft.quackingly.config.QuackinglyConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Client-side voice pipeline controller. Coordinates:
 *
 *   Push-to-talk key down  -> QuackinglyVoiceChatPlugin.startRecording()
 *   Push-to-talk key up    -> QuackinglyVoiceChatPlugin.stopAndTranscribe()
 *                              -> GroqSTT.transcribe(wav)
 *                              -> send text to server as ChatToCompanion packet
 *                              -> server calls LLM, sends CompanionReply packet
 *                              -> onCompanionReply(text) (below)
 *                              -> OpenAITTS.synthesise(text) on worker thread
 *                              -> play MP3 via Java Sound
 *
 * Voice output:
 *   - When server sends a CompanionReplyPayload, we synthesise TTS audio via
 *     OpenAI tts-1 (fastest cloud TTS, ~250ms latency) and play it back.
 *   - TTS calls are made on a worker thread so we don't stall the render thread.
 *   - If voice is disabled in config, we skip TTS and just show the chat line.
 *   - If TTS key is missing or call fails, we fall back to chat-only silently.
 */
public final class ClientVoiceController {

    private ClientVoiceController() {}

    private static boolean pttActive = false;
    /** Cache of recent TTS audio to avoid re-synthesising identical replies. */
    private static final ConcurrentMap<String, byte[]> ttsCache = new ConcurrentHashMap<>();
    private static final int TTS_CACHE_MAX = 32;

    public static void togglePushToTalk() {
        if (!QuackinglyConfig.get().voiceEnabled) {
            MinecraftClient.getInstance().player.sendMessage(
                    Text.translatable("chat.quackingly.voice_disabled").formatted(Formatting.RED));
            return;
        }
        if (pttActive) releasePtt();
        else pressPtt();
    }

    public static void pressPtt() {
        try {
            QuackinglyVoiceChatPlugin.startRecording();
            pttActive = true;
        } catch (Throwable t) {
            Quackingly.LOGGER.warn("Failed to start PTT", t);
        }
    }

    public static void releasePtt() {
        pttActive = false;
        try {
            QuackinglyVoiceChatPlugin.stopAndTranscribe();
        } catch (Throwable t) {
            Quackingly.LOGGER.warn("Failed to release PTT", t);
        }
    }

    /**
     * Called by the SVC plugin after the WAV is collected.
     * We do STT, then forward the text to the server as a chat packet.
     */
    public static void handleCapturedAudio(byte[] wavBytes) {
        MinecraftClient.getInstance().execute(() -> {
            try {
                String text = GroqSTT.transcribe(wavBytes);
                if (text.isBlank()) return;
                MinecraftClient.getInstance().player.sendMessage(
                        Text.literal("[you] " + text).formatted(Formatting.ITALIC, Formatting.GRAY));
                ClientCompanionPackets.sendChatToCompanion(text);
            } catch (Throwable t) {
                Quackingly.LOGGER.warn("STT failed", t);
                MinecraftClient.getInstance().player.sendMessage(
                        Text.literal("(STT failed: " + t.getMessage() + ")").formatted(Formatting.RED));
            }
        });
    }

    /**
     * Called when the server sends us Quackingly's reply text. If voice is
     * enabled and a TTS API key is configured, we synthesise MP3 audio and
     * play it back. Otherwise, we just leave the chat line visible (already
     * shown by the server-side sendMessage call).
     */
    public static void onCompanionReply(String replyText) {
        if (replyText == null || replyText.isBlank()) return;
        QuackinglyConfig.ConfigData cfg = QuackinglyConfig.get();
        if (!cfg.voiceEnabled) return;
        if (cfg.ttsApiKey == null || cfg.ttsApiKey.isBlank()) {
            Quackingly.LOGGER.debug("[Quackingly] Voice enabled but no TTS key set; skipping TTS for reply.");
            return;
        }

        // Run TTS on a worker thread so we don't stall the client tick
        new Thread(() -> {
            try {
                byte[] mp3 = ttsCache.computeIfAbsent(replyText, t -> {
                    try { return OpenAITTS.synthesise(t); }
                    catch (Exception e) {
                        Quackingly.LOGGER.warn("TTS synthesis failed", e);
                        return new byte[0];   // cache the failure so we don't retry
                    }
                });
                if (mp3 != null && mp3.length > 0) {
                    playMp3AtCompanion(mp3);
                }
                // Trim cache if it grew too big
                if (ttsCache.size() > TTS_CACHE_MAX) {
                    ttsCache.clear();
                }
            } catch (Throwable t) {
                Quackingly.LOGGER.warn("TTS playback pipeline failed", t);
            }
        }, "Quackingly-TTS").start();
    }

    /**
     * Play back an MP3 byte[] via Java Sound API. We use SourceDataLine directly
     * (works on Pojav/Mojo too — no MC SoundSystem dependency).
     *
     * Positional audio: future versions can spatialise based on distance to
     * the companion; for v1.1 we just play at fixed volume.
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
                    gain.setValue(-3.0f);   // modest attenuation
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
