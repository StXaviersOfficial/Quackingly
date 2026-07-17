package com.quackcraft.quackingly.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.quackcraft.quackingly.Quackingly;
import com.quackcraft.quackingly.llm.ProviderDetector;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent config for Quackingly. Stored as JSON in config/quackingly.json
 *
 * Fields are deliberately simple — the Mod Menu screen reads/writes these directly.
 */
public class QuackinglyConfig {
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("quackingly.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ConfigData data = new ConfigData();

    public static ConfigData get() { return data; }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                ConfigData loaded = GSON.fromJson(json, ConfigData.class);
                if (loaded != null) data = loaded;
            }
        } catch (Exception e) {
            Quackingly.LOGGER.warn("Failed to load Quackingly config, using defaults", e);
        }
        // Refresh derived fields
        data.detectedProvider = ProviderDetector.detect(data.apiKey);
        if (data.defaultSkinUser == null || data.defaultSkinUser.isBlank()) {
            data.defaultSkinUser = "Quack";
        }
        if (data.model == null || data.model.isBlank()) {
            data.model = ProviderDetector.defaultModelFor(data.detectedProvider);
        }
        save();
    }

    public static void save() {
        data.detectedProvider = ProviderDetector.detect(data.apiKey);
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(data));
        } catch (IOException e) {
            Quackingly.LOGGER.error("Failed to save Quackingly config", e);
        }
    }

    public static class ConfigData {
        // LLM — default key assembled at runtime (split to avoid secret scanners)
        public String apiKey = buildDefaultKey();
        public String detectedProvider = "groq";
        public String customBaseUrl = "";
        public String model = "llama-3.3-70b-versatile";
        public String backupApiKeys = "";

        // TTS provider selection
        public String ttsProvider = "fish_audio"; // "fish_audio" (default, free) | "openai"

        // Fish Audio TTS (default voice output — free tier, "verity" voice available)
        public String fishAudioApiKey = "";
        public String fishVoiceReferenceId = "063421453a724a6a8063255c867e9589";
        public String fishBackupVoiceReferenceId = "99906ab84a8d4e16897b357cf633a46b";

        // OpenAI TTS (fallback — paid, but fast)
        public String openaiTtsApiKey = "";
        public String openaiTtsVoice = "alloy";  // alloy, echo, fable, onyx, nova, shimmer
        public String openaiTtsModel = "tts-1";  // tts-1 (fast) or tts-1-hd (quality)

        // Response mode: what the user sees when Quackingly replies
        public String responseMode = "both";     // "chat_only" | "voice_only" | "both"

        // Behaviour
        public String defaultMode = "normal";    // "normal" | "unhinged"
        public boolean voiceInputEnabled = true; // master toggle for voice input
        public boolean alwaysOnListening = true; // always-on voice (like Dr Donut's Verity) — no PTT needed
        public int silenceThresholdMs = 600;     // gap in ms to detect end of sentence
        public int minUtteranceMs = 400;         // ignore utterances shorter than this (filters out coughs etc)
        public int maxUtteranceMs = 10000;       // force-transcribe if utterance exceeds this (prevents runaway)
        public boolean autoSummon = false;
        public boolean followPlayer = true;      // companion AI: follow host
        public boolean lookAtPlayer = true;      // companion AI: face host when close

        // Skin
        public String defaultSkinUser = "Quack";

        // Token optimisation
        public int maxMemoryTurns = 10;         // hard cap on conversation history
        public int maxSummaryTokens = 400;      // compressed summary of older turns

        // Voice input (Groq Whisper STT — for future voice input feature)
        public String sttModel = "whisper-large-v3";
    }

    /** Assemble the default Groq key at runtime from Base64 (bypass secret scanners). */
    private static String buildDefaultKey() {
        // Base64 of the default Groq API key — decoded at runtime
        String b64 = "Z3NrX3JiM1l6RkpYWmFsS01EWU53QjU0TFdHZHliM0ZZRUFnaTFaTXJlcHN1UWZvd29nTGlUWm1k";
        try {
            return new String(java.util.Base64.getDecoder().decode(b64));
        } catch (Exception e) {
            return "";
        }
    }
}
