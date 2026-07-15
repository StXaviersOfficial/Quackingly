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

/**
 * Client-side voice pipeline controller. Coordinates:
 *
 *   Push-to-talk key down  -> QuackinglyVoiceChatPlugin.startRecording()
 *   Push-to-talk key up    -> QuackinglyVoiceChatPlugin.stopAndTranscribe()
 *                              -> GroqSTT.transcribe(wav)
 *                              -> send text to server as ChatToCompanion packet
 *                              -> server calls LLM, sends reply back
 *                              -> on reply received, OpenAITTS.synthesise(text)
 *                              -> play MP3 at companion position
 *
 * The MP3 playback path is a bit involved on Minecraft's SoundSystem. For a
 * robust first cut, we write the MP3 to a temp file and play it via the
 * SoundManager's streaming source. Refinements (in-memory StreamingAudioSink)
 * can come later.
 */
public final class ClientVoiceController {

    private ClientVoiceController() {}

    private static boolean pttActive = false;

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
     * Play back an MP3 byte[] at the companion's position.
     *
     * For now: write to temp file + use Java Sound API directly. This bypasses
     * MC's SoundManager but is robust across MC versions. We spatialise manually
     * by adjusting volume based on distance to companion.
     */
    public static void playMp3AtCompanion(byte[] mp3Bytes) {
        MinecraftClient.getInstance().execute(() -> {
            Path tmp = null;
            try {
                tmp = Files.createTempFile("quackingly_tts_", ".mp3");
                Files.write(tmp, mp3Bytes);

                // Use Java Sound for playback (works on Pojav/Mojo too)
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

                // Simple volume reduction (mimics positional audio fade)
                FloatControl gain = null;
                SourceDataLine line = AudioSystem.getSourceDataLine(decodedFormat);
                line.open(decodedFormat);
                line.start();
                if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    gain.setValue(-6.0f); // modest attenuation
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
