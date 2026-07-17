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
        boolean ok = ns.spawn();
        if (ok) {
            host.sendMessage(Text.translatable("chat.quackingly.summoned").formatted(Formatting.AQUA));
            SilenceWatcher.onSessionStart(host.getUuid());
        } else {
            host.sendMessage(Text.literal("Quackingly could not spawn. Check the log.").formatted(Formatting.RED));
        }
        return ok;
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
        boolean ok = ns.spawn();
        if (ok) {
            host.sendMessage(Text.translatable("chat.quackingly.summoned").formatted(Formatting.AQUA));
            host.sendMessage(Text.literal("Mode: " + mode).formatted(Formatting.YELLOW));
            SilenceWatcher.onSessionStart(host.getUuid());
        } else {
            host.sendMessage(Text.literal("Quackingly could not spawn. Check the log.").formatted(Formatting.RED));
        }
        return ok;
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

        public boolean spawn() {
            if (server == null) return false;
            try {
                // Call CarpetSpawnHelper, which holds the Carpet-dependent code.
                // This class is only loaded if CARPET_PRESENT is true (checked above).
                Object obj = CarpetSpawnHelper.spawnFakePlayer(server, host);
                if (!(obj instanceof PlayerEntity)) return false;
                fakePlayer = (PlayerEntity) obj;
                SkinApplier.applyDefaultSkin(fakePlayer);
                return true;
            } catch (Throwable t) {
                Quackingly.LOGGER.error("Failed to spawn Quackingly", t);
                return false;
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

            // Refresh key rotation in case the user changed config since last call
            rebuildKeyRotation();

            // Show "thinking" indicator (pre-reply, like a typing indicator — NOT a "speaking" indicator)
            host.sendMessage(Text.literal("Quackingly is thinking...").formatted(Formatting.ITALIC, Formatting.GRAY));

            // Run LLM call on a worker thread so we don't block server tick
            new Thread(() -> {
                try {
                    String currentKey = llmKeyRotation.current();
                    if (currentKey.isBlank()) {
                        server.execute(() -> fakePlayer.sendMessage(
                                Text.literal("(no API key set — open Mod Menu → Quackingly)").formatted(Formatting.RED)));
                        return;
                    }

                    String reply = null;
                    Exception lastError = null;

                    // Try primary key, then backups on 401/402/429 errors
                    while (reply == null && currentKey != null && !currentKey.isBlank()) {
                        try {
                            LLMProvider llm = LLMProvider.fromConfig(currentKey);
                            String ctx = describeWorld(from);
                            String prompt = PromptManager.systemPrompt(mode, from.getName().getString(), ctx);
                            reply = llm.chat(memory.buildForCall(prompt));
                        } catch (LLMProvider.LLMHttpException e) {
                            if (LLMProvider.isKeyOrQuotaError(e.httpStatus) && llmKeyRotation.hasMore()) {
                                Quackingly.LOGGER.warn("[Quackingly] LLM key failed (HTTP {}), trying backup.",
                                        e.httpStatus);
                                currentKey = llmKeyRotation.advance();
                                continue;
                            }
                            lastError = e;
                            break;
                        }
                    }

                    if (reply == null) {
                        final Exception err = lastError;
                        String msg = err != null ? err.getMessage() : "No LLM keys available.";
                        server.execute(() -> fakePlayer.sendMessage(
                                Text.literal("(error: " + msg + ")").formatted(Formatting.RED)));
                        return;
                    }

                    // Memory persists across key swaps — Quackingly never forgets the conversation
                    memory.addAssistant(reply);

                    // Determine what to show based on responseMode
                    String responseMode = QuackinglyConfig.get().responseMode;
                    if (responseMode == null || responseMode.isBlank()) responseMode = "both";

                    // Chat line (visible to host + as a chat bubble above Quackingly)
                    if (responseMode.equals("chat_only") || responseMode.equals("both")) {
                        final String replyText = reply;
                        server.execute(() -> fakePlayer.sendMessage(
                                Text.literal("<Quackingly> " + replyText).formatted(Formatting.WHITE)));
                    }

                    // Voice output (client synthesises TTS from the reply text)
                    if (responseMode.equals("voice_only") || responseMode.equals("both")) {
                        ServerCompanionPackets.sendTtsReply(from, reply);
                    }
                } catch (Exception e) {
                    Quackingly.LOGGER.error("LLM call failed", e);
                    server.execute(() -> fakePlayer.sendMessage(
                            Text.literal("(error: " + e.getMessage() + ")").formatted(Formatting.RED)));
                }
            }, "Quackingly-LLM").start();
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
