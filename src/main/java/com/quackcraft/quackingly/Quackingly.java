package com.quackcraft.quackingly;

import com.quackcraft.quackingly.command.QuackSkinCommand;
import com.quackcraft.quackingly.companion.CompanionBrain;
import com.quackcraft.quackingly.companion.CompanionManager;
import com.quackcraft.quackingly.config.QuackinglyConfig;
import com.quackcraft.quackingly.network.QuackinglyPayloads;
import com.quackcraft.quackingly.network.ServerCompanionPackets;
import com.quackcraft.quackingly.voice.SilenceWatcher;
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
 * - LLM bridge (Groq by default, auto-detects OpenAI / OpenRouter / Anthropic / Gemini / Cerebras / custom)
 * - Voice output via Fish Audio TTS (free, Verity voice) or OpenAI TTS (fallback)
 * - Skin loaded from Mojang session API (default username: "Quack")
 * - Two chat modes: Normal (friendly) and Unhinged (Grok-style roast)
 * - Backup API keys with memory persistence
 * - Response mode: chat_only / voice_only / both
 */
public class Quackingly implements ModInitializer {
    public static final String MOD_ID = "quackingly";
    public static final Logger LOGGER = LoggerFactory.getLogger("Quackingly");

    @Override
    public void onInitialize() {
        QuackinglyConfig.load();
        String ver = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer(MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        LOGGER.info("[Quackingly] Initialising v{}. Default provider hint: {}",
                ver, QuackinglyConfig.get().detectedProvider);

        // CRITICAL: Register payload types BEFORE registering receivers.
        // If this is skipped, Fabric 1.21.1 crashes with:
        //   "Cannot register handler as no payload type has been registered"
        QuackinglyPayloads.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CompanionManager.getInstance().onServerStarted(server);
            SilenceWatcher.start(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SilenceWatcher.stop();
            CompanionManager.getInstance().onServerStopped();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                QuackSkinCommand.register(dispatcher));

        ServerCompanionPackets.register();
        CompanionBrain.registerTickHook();
    }
}
