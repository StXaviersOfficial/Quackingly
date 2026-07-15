package com.quackcraft.quackingly.voice;

import com.quackcraft.quackingly.Quackingly;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicPacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple Voice Chat plugin for Quackingly.
 *
 * Hooks the mic-packet event so that when the user holds the push-to-talk key
 * (set in QuackinglyClient), we accumulate Opus-encoded audio frames, decode
 * them to PCM, and ship the PCM buffer to Groq's Whisper STT endpoint when
 * the user releases the key.
 *
 * The companion's reply is then TTS-synthesised via OpenAI and fed back into
 * SVC's locational audio API to play at the companion's position.
 *
 * SVC requires the plugin class to be listed in fabric.mod.json's
 * "custom": { "voicechat:plugin": "..." } entry. We've done that.
 */
public class QuackinglyVoiceChatPlugin implements VoicechatPlugin {

    private static final AtomicBoolean recording = new AtomicBoolean(false);
    private static MicPacketCollector collector;
    private static VoicechatApi api;

    @Override
    public String getPluginId() {
        return Quackingly.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        QuackinglyVoiceChatPlugin.api = api;
        Quackingly.LOGGER.info("[Quackingly] Simple Voice Chat plugin initialised.");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicPacketEvent.class, QuackinglyVoiceChatPlugin::onMicPacket);
    }

    private static void onMicPacket(MicPacketEvent event) {
        if (!recording.get()) return;
        if (collector == null) {
            collector = new MicPacketCollector(api.createOpusDecoder());
        }
        try {
            collector.append(event.getPacketBuffer());
        } catch (Throwable t) {
            Quackingly.LOGGER.warn("Mic packet collection failed", t);
        }
    }

    // Called from QuackinglyClient when the talk key is pressed/released
    public static void startRecording() {
        if (!recording.compareAndSet(false, true)) return;
        if (collector != null) collector.close();
        collector = new MicPacketCollector(api != null ? api.createOpusDecoder() : null);
    }

    public static void stopAndTranscribe() {
        if (!recording.compareAndSet(true, false)) return;
        MicPacketCollector c = collector;
        collector = null;
        if (c == null) return;
        try {
            byte[] wav = c.toWav();
            ClientVoiceController.handleCapturedAudio(wav);
        } catch (Throwable t) {
            Quackingly.LOGGER.warn("Failed to transcribe captured audio", t);
        } finally {
            c.close();
        }
    }

    public static VoicechatApi getApi() { return api; }
}
