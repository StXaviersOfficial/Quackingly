package com.quackcraft.quackingly.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.quackcraft.quackingly.Quackingly;
import com.quackcraft.quackingly.llm.ProviderDetector;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

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
        // LLM
        public String apiKey = "";
        public String detectedProvider = "groq";
        public String customBaseUrl = "";
        public String model = "llama-3.3-70b-versatile";

        // TTS (OpenAI)
        public String ttsApiKey = "";

        // Behaviour
        public String defaultMode = "normal";  // "normal" | "unhinged"
        public boolean voiceEnabled = true;
        public boolean autoSummon = false;

        // Skin
        public String defaultSkinUser = "Quack";

        // Token optimisation
        public int maxMemoryTurns = 10;       // hard cap on conversation history
        public int maxSummaryTokens = 400;    // compressed summary of older turns
        public boolean enableRagLite = true;  // cache recent facts to avoid re-asking

        // Voice
        public String sttModel = "whisper-large-v3";
        public String ttsVoice = "alloy";     // OpenAI voices: alloy, echo, fable, onyx, nova, shimmer
        public String ttsModel = "tts-1";
    }
}
