package com.quackcraft.quackingly.voice;

/**
 * Collects Opus-encoded mic packets from SVC, decodes them to PCM,
 * and packages the result as a WAV file suitable for Groq Whisper STT.
 *
 * v1.0 — STUB. The actual Opus decode + WAV packaging is being implemented
 * incrementally. For v1.0, voice capture is disabled in the plugin; users
 * chat with Quackingly via text. This class is kept here so v1.1 can wire
 * it up without restructuring.
 *
 * Future plan:
 *   1. Plugin collects MicrophonePacketEvent.getPacketBuffer() (Opus bytes)
 *   2. Decoder (created via VoicechatApi.createOpusDecoder()) -> short[] PCM samples
 *   3. Concatenate samples, write a 16-bit PCM mono 48kHz WAV (RIFF/WAVE/fmt/data)
 *   4. Send WAV to Groq Whisper STT
 *   5. LLM produces reply text
 *   6. OpenAI TTS produces MP3 bytes
 *   7. Play MP3 via Java Sound (or SVC's locational audio API if available)
 */
public class MicPacketCollector {

    public MicPacketCollector(Object decoder) {
        // v1.0: no-op
    }

    public void append(byte[] opusFrame) {
        // v1.0: no-op
    }

    public byte[] toWav() {
        return new byte[0];
    }

    public void close() {
        // v1.0: no-op
    }
}
