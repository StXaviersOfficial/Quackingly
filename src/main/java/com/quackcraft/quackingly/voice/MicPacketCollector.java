package com.quackcraft.quackingly.voice;

import de.maxhenkel.voicechat.api.opus.OpusDecoder;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * Collects Opus-encoded mic packets from SVC, decodes them to PCM,
 * and packages the result as a WAV file suitable for Groq Whisper STT.
 *
 * SVC delivers Opus frames at 48 kHz mono, 20ms each (960 samples).
 */
public class MicPacketCollector {
    private static final int SAMPLE_RATE = 48_000;
    private static final int CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;

    private final OpusDecoder decoder;
    private final ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();

    public MicPacketCollector(OpusDecoder decoder) {
        this.decoder = decoder;
    }

    public void append(byte[] opusFrame) {
        if (decoder == null || opusFrame == null) return;
        try {
            short[] samples = decoder.decode(opusFrame);
            if (samples == null) return;
            ByteBuffer bb = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            ShortBuffer sb = bb.asShortBuffer();
            sb.put(samples);
            pcmOut.write(bb.array());
        } catch (IOException e) {
            // ByteArrayOutputStream doesn't actually throw on write
        }
    }

    public byte[] toWav() throws IOException {
        byte[] pcm = pcmOut.toByteArray();
        ByteArrayOutputStream wav = new ByteArrayOutputStream(pcm.length + 44);

        int chunkSize = 36 + pcm.length;
        int subchunk2Size = pcm.length;
        int byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8;
        int blockAlign = CHANNELS * BITS_PER_SAMPLE / 8;

        // RIFF header
        wav.write("RIFF".getBytes());
        writeIntLE(wav, chunkSize);
        wav.write("WAVE".getBytes());

        // fmt chunk
        wav.write("fmt ".getBytes());
        writeIntLE(wav, 16);         // PCM chunk size
        writeShortLE(wav, (short) 1); // PCM
        writeShortLE(wav, (short) CHANNELS);
        writeIntLE(wav, SAMPLE_RATE);
        writeIntLE(wav, byteRate);
        writeShortLE(wav, (short) blockAlign);
        writeShortLE(wav, (short) BITS_PER_SAMPLE);

        // data chunk
        wav.write("data".getBytes());
        writeIntLE(wav, subchunk2Size);
        wav.write(pcm);

        return wav.toByteArray();
    }

    private static void writeIntLE(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF); o.write((v >> 8) & 0xFF); o.write((v >> 16) & 0xFF); o.write((v >> 24) & 0xFF);
    }
    private static void writeShortLE(ByteArrayOutputStream o, short v) {
        o.write(v & 0xFF); o.write((v >> 8) & 0xFF);
    }

    public void close() {
        try { pcmOut.close(); } catch (IOException ignored) {}
        if (decoder != null) decoder.close();
    }
}
