package com.quackcraft.quackingly;

import com.quackcraft.quackingly.command.QuackSkinCommand;
import com.quackcraft.quackingly.companion.CompanionManager;
import com.quackcraft.quackingly.config.QuackinglyConfig;
import com.quackcraft.quackingly.network.ServerCompanionPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main mod entrypoint. Shared (both client + dedicated server).
 *
 * Quackingly is a non-horror, Verity-style AI companion for Minecraft Fabric 1.21.1.
 * - Player-model bot via Carpet fake player system
 * - LLM bridge (Groq by default, auto-detects OpenAI / OpenRouter / Anthropic / Gemini / custom)
 * - Voice via Simple Voice Chat (Groq Whisper STT + OpenAI TTS)
 * - Skin loaded from Mojang session API (default username: "Quack")
 * - Two chat modes: Normal (friendly) and Unhinged (Grok-style roast)
 */
public class Quackingly implements ModInitializer {
    public static final String MOD_ID = "quackingly";
    public static final Logger LOGGER = LoggerFactory.getLogger("Quackingly");

    @Override
    public void onInitialize() {
        QuackinglyConfig.load();
        LOGGER.info("[Quackingly] Initialising. Default provider hint: {}",
                QuackinglyConfig.get().detectedProvider);

        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                CompanionManager.getInstance().onServerStarted(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                CompanionManager.getInstance().onServerStopped());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                QuackSkinCommand.register(dispatcher));

        ServerCompanionPackets.register();
    }
}
