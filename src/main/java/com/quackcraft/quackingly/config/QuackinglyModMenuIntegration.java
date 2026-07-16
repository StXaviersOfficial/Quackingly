package com.quackcraft.quackingly.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Mod Menu integration entrypoint. Builds the config screen using Cloth Config.
 *
 * Categories:
 *   - LLM        (API key, backup keys, provider, model, custom URL, response mode)
 *   - Voice      (TTS provider, Fish Audio key + voice, OpenAI key + voice, STT model)
 *   - Companion  (chat mode, default skin, auto-summon, follow/look toggles, memory)
 */
public class QuackinglyModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return this::buildScreen;
    }

    private Screen buildScreen(Screen parent) {
        QuackinglyConfig.ConfigData cfg = QuackinglyConfig.get();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("screen.quackingly.config.title"));

        ConfigEntryBuilder eb = builder.entryBuilder();

        // ---- LLM category ----
        ConfigCategory llm = builder.getOrCreateCategory(Text.literal("LLM (Brain)"));
        llm.addEntry(eb.startStrField(
                        Text.literal("Primary API Key"), cfg.apiKey)
                .setDefaultValue("")
                .setTooltip(Text.literal("Groq (gsk_), OpenAI (sk-), OpenRouter (sk-or-), Anthropic (sk-ant-), Gemini (AIza). For Cerebras, set key + Custom URL below."))
                .setSaveConsumer(v -> cfg.apiKey = v)
                .build());
        llm.addEntry(eb.startStrField(
                        Text.literal("Backup API Keys (comma-separated)"), cfg.backupApiKeys)
                .setDefaultValue("")
                .setTooltip(Text.literal("Comma-separated. When the primary key fails (401/402/429), the next backup is tried automatically. Memory persists across swaps."))
                .setSaveConsumer(v -> cfg.backupApiKeys = v)
                .build());
        llm.addEntry(eb.startStrField(
                        Text.literal("Custom API Base URL (optional)"), cfg.customBaseUrl)
                .setDefaultValue("")
                .setTooltip(Text.literal("For Cerebras: https://inference.cerebras.ai/v1 — overrides provider URL"))
                .setSaveConsumer(v -> cfg.customBaseUrl = v)
                .build());
        llm.addEntry(eb.startStrField(
                        Text.literal("Model"), cfg.model)
                .setDefaultValue("llama-3.3-70b-versatile")
                .setTooltip(Text.literal("Groq: llama-3.3-70b-versatile | Cerebras: llama3.1-70b | OpenAI: gpt-4o-mini"))
                .setSaveConsumer(v -> cfg.model = v)
                .build());
        llm.addEntry(eb.startSelector(
                        Text.literal("Response Mode"), new String[]{"both", "chat_only", "voice_only"}, cfg.responseMode)
                .setDefaultValue("both")
                .setTooltip(Text.literal("both = chat line + voice | chat_only = text only | voice_only = audio only (no chat text)"))
                .setSaveConsumer(v -> cfg.responseMode = v)
                .build());

        // ---- Voice category ----
        ConfigCategory voice = builder.getOrCreateCategory(Text.literal("Voice (TTS)"));
        voice.addEntry(eb.startSelector(
                        Text.literal("TTS Provider"), new String[]{"fish_audio", "openai"}, cfg.ttsProvider)
                .setDefaultValue("fish_audio")
                .setTooltip(Text.literal("fish_audio = free tier, 'verity' voice available (DEFAULT) | openai = paid, fast"))
                .setSaveConsumer(v -> cfg.ttsProvider = v)
                .build());

        // Fish Audio fields
        voice.addEntry(eb.startStrField(
                        Text.literal("Fish Audio API Key"), cfg.fishAudioApiKey)
                .setDefaultValue("")
                .setTooltip(Text.literal("Get a free key at fish.audio — needed for voice output"))
                .setSaveConsumer(v -> cfg.fishAudioApiKey = v)
                .build());
        voice.addEntry(eb.startStrField(
                        Text.literal("Fish Audio Voice ID (primary)"), cfg.fishVoiceReferenceId)
                .setDefaultValue("063421453a724a6a8063255c867e9589")
                .setTooltip(Text.literal("Fish Audio reference_id for the voice. Default = 'verity' voice."))
                .setSaveConsumer(v -> cfg.fishVoiceReferenceId = v)
                .build());
        voice.addEntry(eb.startStrField(
                        Text.literal("Fish Audio Voice ID (backup)"), cfg.fishBackupVoiceReferenceId)
                .setDefaultValue("99906ab84a8d4e16897b357cf633a46b")
                .setTooltip(Text.literal("Fallback voice if primary is rejected (422 error)."))
                .setSaveConsumer(v -> cfg.fishBackupVoiceReferenceId = v)
                .build());

        // OpenAI TTS fields (fallback)
        voice.addEntry(eb.startStrField(
                        Text.literal("OpenAI TTS API Key (fallback)"), cfg.openaiTtsApiKey)
                .setDefaultValue("")
                .setTooltip(Text.literal("Only needed if TTS Provider = openai. Paid."))
                .setSaveConsumer(v -> cfg.openaiTtsApiKey = v)
                .build());
        voice.addEntry(eb.startStrField(
                        Text.literal("OpenAI TTS Voice"), cfg.openaiTtsVoice)
                .setDefaultValue("alloy")
                .setTooltip(Text.literal("alloy, echo, fable, onyx, nova, shimmer"))
                .setSaveConsumer(v -> cfg.openaiTtsVoice = v)
                .build());
        voice.addEntry(eb.startStrField(
                        Text.literal("OpenAI TTS Model"), cfg.openaiTtsModel)
                .setDefaultValue("tts-1")
                .setTooltip(Text.literal("tts-1 (fast, ~250ms) or tts-1-hd (slower, higher quality)"))
                .setSaveConsumer(v -> cfg.openaiTtsModel = v)
                .build());
        voice.addEntry(eb.startStrField(
                        Text.literal("STT Model (Groq Whisper, for future voice input)"), cfg.sttModel)
                .setDefaultValue("whisper-large-v3")
                .setSaveConsumer(v -> cfg.sttModel = v)
                .build());

        // ---- Companion category ----
        ConfigCategory comp = builder.getOrCreateCategory(Text.literal("Companion"));
        comp.addEntry(eb.startStrField(
                        Text.literal("Default Skin Username"), cfg.defaultSkinUser)
                .setDefaultValue("Quack")
                .setSaveConsumer(v -> cfg.defaultSkinUser = v)
                .build());
        comp.addEntry(eb.startSelector(
                        Text.literal("Default Chat Mode"), new String[]{"normal", "unhinged"}, cfg.defaultMode)
                .setDefaultValue("normal")
                .setSaveConsumer(v -> cfg.defaultMode = v)
                .build());
        comp.addEntry(eb.startBooleanToggle(
                        Text.literal("Auto-summon on world join"), cfg.autoSummon)
                .setDefaultValue(false)
                .setSaveConsumer(v -> cfg.autoSummon = v)
                .build());
        comp.addEntry(eb.startBooleanToggle(
                        Text.literal("Follow player (AI)"), cfg.followPlayer)
                .setDefaultValue(true)
                .setSaveConsumer(v -> cfg.followPlayer = v)
                .build());
        comp.addEntry(eb.startBooleanToggle(
                        Text.literal("Look at player (AI)"), cfg.lookAtPlayer)
                .setDefaultValue(true)
                .setSaveConsumer(v -> cfg.lookAtPlayer = v)
                .build());
        comp.addEntry(eb.startIntField(
                        Text.literal("Max conversation turns in memory"), cfg.maxMemoryTurns)
                .setDefaultValue(10)
                .setMin(2).setMax(50)
                .setTooltip(Text.literal("Token optimisation: older turns are summarised into a compact memory block."))
                .setSaveConsumer(v -> cfg.maxMemoryTurns = v)
                .build());

        builder.setSavingRunnable(QuackinglyConfig::save);
        return builder.build();
    }
}
