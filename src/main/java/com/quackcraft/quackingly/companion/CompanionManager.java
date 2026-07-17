package com.quackcraft.quackingly.companion;

import com.quackcraft.quackingly.Quackingly;
import com.quackcraft.quackingly.config.QuackinglyConfig;
import com.quackcraft.quackingly.llm.ConversationMemory;
import com.quackcraft.quackingly.llm.KeyRotation;
import com.quackcraft.quackingly.llm.LLMProvider;
import com.quackcraft.quackingly.llm.PromptManager;
import com.quackcraft.quackingly.network.ServerCompanionPackets;
import com.quackcraft.quackingly.skin.SkinApplier;
import com.quackcraft.quackingly.voice.SilenceWatcher;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-server manager for the Quackingly companion.
 *
 * Uses Carpet's EntityPlayerMPFake (when Carpet is installed) to spawn a real
 * fake-player that looks/acts like a normal player. One Quackingly per world
 * (per online player host).
 *
 * Carpet is optional — if absent, summon fails gracefully with a friendly message.
 *
 * Token optimisation:
 *   - We only call the LLM when there's a new user message (event-driven, not tick-polling).
 *   - Each player's conversation uses a ConversationMemory that auto-compacts.
 *   - Per-call context is bounded (~2k tokens) regardless of session length.
 */
public class CompanionManager {
    private static final CompanionManager INSTANCE = new CompanionManager();
    private static final boolean CARPET_PRESENT =
            FabricLoader.getInstance().isModLoaded("carpet");

    private MinecraftServer server;
    private final ConcurrentHashMap<UUID, CompanionSession> sessions = new ConcurrentHashMap<>();

    public static CompanionManager getInstance() { return INSTANCE; }
    public static boolean isCarpetPresent() { return CARPET_PRESENT; }

    public void onServerStarted(MinecraftServer server) {
        this.server = server;
        Quackingly.LOGGER.info("[Quackingly] Server started. Carpet present: {}", CARPET_PRESENT);
    }

    public void onServerStopped() {
        for (CompanionSession s : sessions.values()) s.despawn();
        sessions.clear();
        server = null;
    }

    public MinecraftServer getServer() { return server; }

    /** Toggle summon/despawn for the given player's Quackingly. */
    public boolean toggle(ServerPlayerEntity host) {
        CompanionSession s = sessions.get(host.getUuid());
        if (s != null && s.isAlive()) {
            s.despawn();
            sessions.remove(host.getUuid());
            SilenceWatcher.onSessionEnd(host.getUuid());
            host.sendMessage(Text.translatable("chat.quackingly.despawned").formatted(Formatting.YELLOW));
            return false;
        }
        if (!CARPET_PRESENT) {
            host.sendMessage(Text.literal("Quackingly needs the Carpet mod to spawn. " +
                    "Install Carpet (1.21.1) from Modrinth and try again.").formatted(Formatting.RED));
            return false;
        }
        CompanionSession ns = new CompanionSession(host);
        sessions.put(host.getUuid(), ns);
        host.sendMessage(Text.literal("Spawning Quackingly...").formatted(Formatting.GRAY));
        ns.spawnAsync(ok -> {
            if (ok) {
                host.sendMessage(Text.translatable("chat.quackingly.summoned").formatted(Formatting.AQUA));
                SilenceWatcher.onSessionStart(host.getUuid());
                // Warn if voice input won't work (Opus broken on this platform)
                if (!com.quackcraft.quackingly.voice.QuackinglyVoiceChatPlugin.isOpusAvailable()) {
                    host.sendMessage(Text.literal("⚠ Voice input unavailable on this platform (Opus native lib missing). " +
                            "Use text chat to talk to Quackingly. Voice output (TTS) still works.")
                            .formatted(Formatting.YELLOW));
                }
            } else {
                sessions.remove(host.getUuid());
                host.sendMessage(Text.literal("Quackingly could not spawn. Check the log.").formatted(Formatting.RED));
            }
        });
        return true;
    }

    /** Send a chat message TO Quackingly on behalf of a player. */
    public void sendToCompanion(ServerPlayerEntity host, String message) {
        CompanionSession s = sessions.get(host.getUuid());
        if (s == null || !s.isAlive()) {
            host.sendMessage(Text.literal("Summon Quackingly first (press K or type /quackingly).").formatted(Formatting.RED));
            return;
        }
        s.handleUserMessage(host, message);
    }

    /**
     * Summon Quackingly with a specific mode (called from the /quackingly
     * confirmation flow). If already summoned, despawn first then respawn
     * with the new mode.
     */
    public boolean summonWithMode(ServerPlayerEntity host, String mode) {
        // Despawn existing session if any
        CompanionSession existing = sessions.get(host.getUuid());
        if (existing != null && existing.isAlive()) {
            existing.despawn();
            sessions.remove(host.getUuid());
            SilenceWatcher.onSessionEnd(host.getUuid());
        }

        if (!CARPET_PRESENT) {
            host.sendMessage(Text.literal("Quackingly needs the Carpet mod to spawn. " +
                    "Install Carpet (1.21.1) from Modrinth and try again.").formatted(Formatting.RED));
            return false;
        }

        CompanionSession ns = new CompanionSession(host);
        ns.setMode(mode);
        sessions.put(host.getUuid(), ns);
        host.sendMessage(Text.literal("Spawning Quackingly...").formatted(Formatting.GRAY));
        ns.spawnAsync(ok -> {
            if (ok) {
                host.sendMessage(Text.translatable("chat.quackingly.summoned").formatted(Formatting.AQUA));
                host.sendMessage(Text.literal("Mode: " + mode).formatted(Formatting.YELLOW));
                SilenceWatcher.onSessionStart(host.getUuid());
            } else {
                sessions.remove(host.getUuid());
                host.sendMessage(Text.literal("Quackingly could not spawn. Check the log.").formatted(Formatting.RED));
            }
        });
        return true;
    }

    public CompanionSession getSession(ServerPlayerEntity host) {
        return sessions.get(host.getUuid());
    }

    /** Read-only view of all active sessions (used by CompanionBrain for tick). */
    public java.util.Collection<CompanionSession> sessionsView() {
        return sessions.values();
    }

    /* ---- Per-player session ---- */

    public class CompanionSession {
        private final ServerPlayerEntity host;
        private PlayerEntity fakePlayer;       // EntityPlayerMPFake at runtime (when Carpet is present)
        private final ConversationMemory memory = new ConversationMemory();
        private KeyRotation llmKeyRotation;     // rebuilt when config changes
        private String mode;

        public CompanionSession(ServerPlayerEntity host) {
            this.host = host;
            this.mode = QuackinglyConfig.get().defaultMode;
            rebuildKeyRotation();
        }

        /** Rebuild the key rotation from current config. Call when config changes. */
        public void rebuildKeyRotation() {
            QuackinglyConfig.ConfigData cfg = QuackinglyConfig.get();
            llmKeyRotation = new KeyRotation(cfg.apiKey, cfg.backupApiKeys);
        }

        public ServerPlayerEntity getHost() { return host; }

        /**
         * Spawn the fake player. createFake is called synchronously on the server thread
         * (required by Carpet), then the lookup happens on a worker thread (with retries
         * because Carpet's player join is async). The caller's success/failure message
         * is sent after the lookup completes.
         *
         * @param onComplete called on the server thread after spawn succeeds (true) or fails (false)
         */
        public void spawnAsync(java.util.function.Consumer<Boolean> onComplete) {
            if (server == null) { onComplete.accept(false); return; }
            try {
                // Step 1: createFake MUST be on the server thread (we already are)
                CarpetSpawnHelper.createFakePlayer(server, host);

                // Step 2: lookup on a worker thread (createFake's join is async)
                new Thread(() -> {
                    try {
                        ServerPlayerEntity found = CarpetSpawnHelper.lookupFakePlayer(server);
                        if (found == null) {
                            server.execute(() -> onComplete.accept(false));
                            return;
                        }
                        fakePlayer = found;
                        // Apply skin on the server thread (modifies GameProfile)
                        server.execute(() -> {
                            SkinApplier.applyDefaultSkin(fakePlayer);
                            onComplete.accept(true);
                        });
                    } catch (Throwable t) {
                        Quackingly.LOGGER.error("Failed to look up spawned Quackingly", t);
                        server.execute(() -> onComplete.accept(false));
                    }
                }, "Quackingly-Spawn-Lookup").start();
            } catch (Throwable t) {
                Quackingly.LOGGER.error("Failed to spawn Quackingly", t);
                onComplete.accept(false);
            }
        }

        public void despawn() {
            if (fakePlayer != null) {
                try { fakePlayer.kill(); } catch (Throwable ignored) {}
                fakePlayer = null;
            }
        }

        public boolean isAlive() { return fakePlayer != null && !fakePlayer.isRemoved(); }

        public PlayerEntity getFakePlayer() { return fakePlayer; }

        public void setMode(String m) { this.mode = m; }
        public String getMode() { return mode; }

        public void handleUserMessage(ServerPlayerEntity from, String text) {
            memory.addUser("[" + from.getName().getString() + "] " + text);
            rebuildKeyRotation();
            host.sendMessage(Text.literal("Quackingly is thinking...").formatted(Formatting.ITALIC, Formatting.GRAY));

            new Thread(() -> {
                try {
                    String currentKey = llmKeyRotation.current();
                    if (currentKey.isBlank()) {
                        server.execute(() -> host.sendMessage(
                                Text.literal("§c[Quackingly] No API key set! Open Mod Menu → Quackingly → LLM → API Key")
                                        .formatted(Formatting.RED)));
                        return;
                    }

                    String reply = null;
                    Exception lastError = null;

                    while (reply == null && currentKey != null && !currentKey.isBlank()) {
                        try {
                            LLMProvider llm = LLMProvider.fromConfig(currentKey);
                            String ctx = describeWorld(from);
                            String prompt = PromptManager.systemPrompt(mode, from.getName().getString(), ctx);
                            reply = llm.chat(memory.buildForCall(prompt));
                        } catch (LLMProvider.LLMHttpException e) {
                            Quackingly.LOGGER.error("[Quackingly] LLM HTTP {}: {}", e.httpStatus, e.getMessage());
                            if (LLMProvider.isKeyOrQuotaError(e.httpStatus) && llmKeyRotation.hasMore()) {
                                currentKey = llmKeyRotation.advance();
                                continue;
                            }
                            lastError = e;
                            break;
                        } catch (Exception e) {
                            Quackingly.LOGGER.error("[Quackingly] LLM call error", e);
                            lastError = e;
                            break;
                        }
                    }

                    if (reply == null) {
                        final Exception err = lastError;
                        String msg;
                        if (err instanceof LLMProvider.LLMHttpException) {
                            int status = ((LLMProvider.LLMHttpException) err).httpStatus;
                            msg = friendlyLlmError(status);
                        } else if (err != null) {
                            msg = err.getMessage();
                        } else {
                            msg = "No LLM keys available.";
                        }
                        Quackingly.LOGGER.error("[Quackingly] LLM failed, showing to host: {}", msg);
                        final String errMsg = msg;
                        server.execute(() -> host.sendMessage(
                                Text.literal("§c[Quackingly] " + errMsg).formatted(Formatting.RED)));
                        return;
                    }

                    memory.addAssistant(reply);
                    String responseMode = QuackinglyConfig.get().responseMode;
                    if (responseMode == null || responseMode.isBlank()) responseMode = "both";

                    if (responseMode.equals("chat_only") || responseMode.equals("both")) {
                        final String replyText = reply;
                        server.execute(() -> {
                            if (fakePlayer != null && !fakePlayer.isRemoved()) {
                                fakePlayer.sendMessage(Text.literal("<Quackingly> " + replyText).formatted(Formatting.WHITE));
                            } else {
                                host.sendMessage(Text.literal("<Quackingly> " + replyText).formatted(Formatting.WHITE));
                            }
                        });
                    }
                    if (responseMode.equals("voice_only") || responseMode.equals("both")) {
                        ServerCompanionPackets.sendTtsReply(from, reply);
                    }
                } catch (Exception e) {
                    Quackingly.LOGGER.error("[Quackingly] LLM call failed", e);
                    server.execute(() -> host.sendMessage(
                            Text.literal("§c[Quackingly] Error: " + e.getMessage()).formatted(Formatting.RED)));
                }
            }, "Quackingly-LLM").start();
        }

        private static String friendlyLlmError(int status) {
            switch (status) {
                case 401: return "API key is invalid. Get a new Groq key at console.groq.com/keys";
                case 402: return "API quota exhausted. You need to add credits or wait.";
                case 403: return "API key is forbidden/revoked. Get a new Groq key at console.groq.com/keys";
                case 429: return "Rate limited — too many requests. Wait a moment and try again.";
                case 500: case 502: case 503: return "LLM server error. Try again in a moment.";
                default:  return "LLM error (HTTP " + status + "). Check console for details.";
            }
        }

        private String describeWorld(ServerPlayerEntity p) {
            try {
                // Richer world context — helps the LLM respond like a real player would
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Player %s at (%.0f, %.0f, %.0f) in %s, %s, time %d ticks",
                        p.getName().getString(),
                        p.getX(), p.getY(), p.getZ(),
                        p.getWorld().getRegistryKey().getValue(),
                        p.getWorld().isNight() ? "night" : "day",
                        p.getWorld().getTimeOfDay() % 24000));
                sb.append(String.format("; health %.0f/%.0f, hunger %d/20, air %d",
                        p.getHealth(), p.getMaxHealth(), p.getHungerManager().getFoodLevel(), p.getAir()));
                if (p.isOnFire()) sb.append("; on fire");
                if (p.isTouchingWater()) sb.append("; in water");
                if (p.isSneaking()) sb.append("; sneaking");
                if (p.isSprinting()) sb.append("; sprinting");
                return sb.toString();
            } catch (Throwable t) {
                return "in Minecraft";
            }
        }
    }
}
