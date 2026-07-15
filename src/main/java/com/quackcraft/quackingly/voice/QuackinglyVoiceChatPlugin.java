package com.quackcraft.quackingly.voice;

import com.quackcraft.quackingly.Quackingly;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple Voice Chat plugin for Quackingly.
 *
 * v1.0 — stub. We register as an SVC plugin so SVC knows we exist, and we expose
 * start/stop recording state for future use. The actual Opus decode + Groq Whisper
 * STT pipeline is being implemented incrementally — for v1.0 users can chat with
 * Quackingly via the in-game text chat and /quack commands.
 *
 * Future versions will hook MicrophonePacketEvent to capture audio when the
 * push-to-talk key is held, decode Opus -> PCM -> WAV, and ship the WAV to Groq
 * Whisper for transcription. The reply is then TTS-synthesised via OpenAI and
 * played back at the companion's position via SVC's locational audio API.
 */
public class QuackinglyVoiceChatPlugin implements VoicechatPlugin {

    private static final AtomicBoolean recording = new AtomicBoolean(false);
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
        // v1.0: no event handlers registered yet.
        // v1.1 will register MicrophonePacketEvent + LocationSoundEvent handlers
        // to capture player mic input and play back Quackingly's TTS audio.
    }

    public static void startRecording() {
        recording.set(true);
    }

    public static void stopAndTranscribe() {
        recording.set(false);
        // v1.0: no-op. Future: transcribe captured audio via Groq Whisper.
    }

    public static VoicechatApi getApi() { return api; }
    public static boolean isRecording() { return recording.get(); }
}
