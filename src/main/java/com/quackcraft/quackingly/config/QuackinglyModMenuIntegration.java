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
 * The screen has 3 categories:
 *   - LLM       (API key, provider, model, custom URL)
 *   - Voice     (TTS key, voice, model, enable toggle)
 *   - Companion (mode, default skin, auto-summon, memory)
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
        ConfigCategory llm = builder.getOrCreateCategory(Text.literal("LLM"));
        llm.addEntry(eb.startStrField(
                        Text.translatable("screen.quackingly.config.api_key"), cfg.apiKey)
                .setDefaultValue("")
                .setTooltip(Text.literal("Auto-detects: gsk_=Groq, sk-or-=OpenRouter, sk-=OpenAI, sk-ant-=Anthropic, AIza=Gemini"))
                .setSaveConsumer(v -> cfg.apiKey = v)
                .build());
        llm.addEntry(eb.startStrField(
                        Text.translatable("screen.quackingly.config.custom_url"), cfg.customBaseUrl)
                .setDefaultValue("")
                .setTooltip(Text.literal("Optional. e.g. https://openrouter.ai/api/v1 — overrides provider URL"))
                .setSaveConsumer(v -> cfg.customBaseUrl = v)
                .build());
        llm.addEntry(eb.startStrField(
                        Text.translatable("screen.quackingly.config.model"), cfg.model)
                .setDefaultValue("llama-3.3-70b-versatile")
                .setSaveConsumer(v -> cfg.model = v)
                .build());
        llm.addEntry(eb.startStrField(
                        Text.translatable("screen.quackingly.config.tts_key"), cfg.ttsApiKey)
                .setDefaultValue("")
                .setTooltip(Text.literal("OpenAI key for TTS. Required only if voice is enabled."))
                .setSaveConsumer(v -> cfg.ttsApiKey = v)
                .build());

        // ---- Companion category ----
        ConfigCategory comp = builder.getOrCreateCategory(Text.literal("Companion"));
        comp.addEntry(eb.startStrField(
                        Text.translatable("screen.quackingly.config.default_skin"), cfg.defaultSkinUser)
                .setDefaultValue("Quack")
                .setSaveConsumer(v -> cfg.defaultSkinUser = v)
                .build());
        comp.addEntry(eb.startSelector(
                        Text.translatable("screen.quackingly.config.mode"),
                        new String[]{"normal", "unhinged"},
                        cfg.defaultMode)
                .setDefaultValue("normal")
                .setSaveConsumer(v -> cfg.defaultMode = v)
                .build());
        comp.addEntry(eb.startBooleanToggle(
                        Text.translatable("screen.quackingly.config.auto_summon"), cfg.autoSummon)
                .setDefaultValue(false)
                .setSaveConsumer(v -> cfg.autoSummon = v)
                .build());
        comp.addEntry(eb.startIntField(
                        Text.translatable("screen.quackingly.config.max_memory"), cfg.maxMemoryTurns)
                .setDefaultValue(10)
                .setMin(2).setMax(50)
                .setSaveConsumer(v -> cfg.maxMemoryTurns = v)
                .build());

        // ---- Voice category ----
        ConfigCategory voice = builder.getOrCreateCategory(Text.literal("Voice"));
        voice.addEntry(eb.startBooleanToggle(
                        Text.translatable("screen.quackingly.config.voice_enabled"), cfg.voiceEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> cfg.voiceEnabled = v)
                .build());
        voice.addEntry(eb.startStrField(
                        Text.literal("STT model (Groq Whisper)"), cfg.sttModel)
                .setDefaultValue("whisper-large-v3")
                .setSaveConsumer(v -> cfg.sttModel = v)
                .build());
        voice.addEntry(eb.startStrField(
                        Text.literal("TTS voice (OpenAI)"), cfg.ttsVoice)
                .setDefaultValue("alloy")
                .setSaveConsumer(v -> cfg.ttsVoice = v)
                .build());
        voice.addEntry(eb.startStrField(
                        Text.literal("TTS model (OpenAI)"), cfg.ttsModel)
                .setDefaultValue("tts-1")
                .setSaveConsumer(v -> cfg.ttsModel = v)
                .build());

        builder.setSavingRunnable(QuackinglyConfig::save);
        return builder.build();
    }
}
