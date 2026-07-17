package com.quackcraft.quackingly.voice;

import com.quackcraft.quackingly.Quackingly;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Voice Chat plugin for Quackingly.
 *
 * REAL IMPLEMENTATION — captures microphone audio from SVC.
 *
 * How it works (this is how Verity does it):
 *   1. Player holds push-to-talk key → client sends VoiceInputStartPayload to server
 *   2. Server marks this player as "recording" — starts a MicPacketCollector for them
 *   3. SVC fires MicrophonePacketEvent on the server for every Opus frame the player sends
 *      (20ms each, 960 samples at 48kHz mono)
 *   4. We decode each Opus frame via api.createDecoder().decode(opusBytes) → short[] PCM
 *   5. Collector appends PCM samples
 *   6. Player releases PTT → client sends VoiceInputStopPayload
 *   7. Server stops the collector, converts PCM → WAV, sends to Groq Whisper STT
 *   8. STT returns text → CompanionManager.sendToCompanion() → LLM → reply
 *
 * The MicrophonePacketEvent is a SERVER-side event (the player's mic audio arrives
 * at the server via SVC's UDP transport). This is perfect — we do the Opus decode
 * + STT call on the server, so the client doesn't need to ship WAV bytes over the
 * network.
 */
public class QuackinglyVoiceChatPlugin implements VoicechatPlugin {

    private static VoicechatApi api;
    private static OpusDecoder sharedDecoder;

    /** Per-player collectors. Present only while the player is actively recording (PTT held). */
    private static final ConcurrentHashMap<UUID, MicPacketCollector> activeCollectors = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return Quackingly.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        QuackinglyVoiceChatPlugin.api = api;
        Quackingly.LOGGER.info("[Quackingly] Simple Voice Chat plugin initialised — mic capture ACTIVE.");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // Hook MicrophonePacketEvent — fires on the server for every Opus frame a player sends.
        registration.registerEvent(MicrophonePacketEvent.class, QuackinglyVoiceChatPlugin::onMicPacket);
        Quackingly.LOGGER.info("[Quackingly] Registered MicrophonePacketEvent handler.");
    }

    private static void onMicPacket(MicrophonePacketEvent event) {
        try {
            // Only care about packets from players who are actively recording
            if (event.getSenderConnection() == null) return;
            if (event.getSenderConnection().getPlayer() == null) return;

            UUID playerUuid = event.getSenderConnection().getPlayer().getUuid();
            MicPacketCollector collector = activeCollectors.get(playerUuid);
            if (collector == null) return; // player isn't recording — ignore

            // Get the Opus-encoded audio bytes from the packet
            MicrophonePacket packet = event.getPacket();
            byte[] opusData = packet.getOpusEncodedData();
            if (opusData == null || opusData.length == 0) return;

            // Decode Opus → PCM (short[], 16-bit, 48kHz mono, 960 samples = 20ms)
            OpusDecoder decoder = getDecoder();
            if (decoder == null) return;
            short[] pcm = decoder.decode(opusData);
            if (pcm == null || pcm.length == 0) return;

            collector.appendSamples(pcm);
        } catch (Throwable t) {
            Quackingly.LOGGER.debug("[Quackingly] Mic packet handling failed: {}", t.toString());
        }
    }

    private static OpusDecoder getDecoder() {
        if (sharedDecoder == null && api != null) {
            try {
                sharedDecoder = api.createDecoder();
            } catch (Throwable t) {
                Quackingly.LOGGER.warn("[Quackingly] Failed to create Opus decoder", t);
            }
        }
        return sharedDecoder;
    }

    // ===== Called by ServerCompanionPackets when client toggles PTT =====

    public static void startRecording(UUID playerUuid) {
        // Replace any existing collector for this player
        MicPacketCollector old = activeCollectors.put(playerUuid, new MicPacketCollector());
        if (old != null) old.close();
        Quackingly.LOGGER.debug("[Quackingly] Started mic recording for player {}", playerUuid);
    }

    /**
     * Stop recording, return the captured WAV bytes (or null if nothing captured).
     * The collector is removed after this call.
     */
    public static byte[] stopRecording(UUID playerUuid) {
        MicPacketCollector collector = activeCollectors.remove(playerUuid);
        if (collector == null) return null;
        try {
            byte[] wav = collector.toWav();
            Quackingly.LOGGER.debug("[Quackingly] Stopped mic recording for player {}: {} bytes WAV",
                    playerUuid, wav == null ? 0 : wav.length);
            return wav;
        } finally {
            collector.close();
        }
    }

    public static boolean isRecording(UUID playerUuid) {
        return activeCollectors.containsKey(playerUuid);
    }

    public static VoicechatApi getApi() { return api; }
}
