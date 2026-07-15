package com.quackcraft.quackingly.companion;

import carpet.CarpetSettings;
import carpet.patches.EntityPlayerMPFake;
import com.quackcraft.quackingly.Quackingly;
import com.quackcraft.quackingly.config.QuackinglyConfig;
import com.quackcraft.quackingly.llm.ChatMessage;
import com.quackcraft.quackingly.llm.ConversationMemory;
import com.quackcraft.quackingly.llm.LLMProvider;
import com.quackcraft.quackingly.llm.PromptManager;
import com.quackcraft.quackingly.skin.SkinApplier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-server manager for the Quackingly companion.
 *
 * Uses Carpet's EntityPlayerMPFake to spawn a real fake-player that looks/acts
 * like a normal player. One Quackingly per world (per online player host).
 *
 * Token optimisation:
 *   - We only call the LLM when there's a new user message (event-driven, not tick-polling).
 *   - Each player's conversation uses a ConversationMemory that auto-compacts.
 *   - Per-call context is bounded (~2k tokens) regardless of session length.
 */
public class CompanionManager {
    private static final CompanionManager INSTANCE = new CompanionManager();

    private MinecraftServer server;
    private final ConcurrentHashMap<UUID, CompanionSession> sessions = new ConcurrentHashMap<>();

    public static CompanionManager getInstance() { return INSTANCE; }

    public void onServerStarted(MinecraftServer server) {
        this.server = server;
        // Carpet must allow fake players
        CarpetSettings.allowSpawningOfflinePlayers = true;
        Quackingly.LOGGER.info("[Quackingly] Server started, fake-player support enabled.");
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
            host.sendMessage(Text.translatable("chat.quackingly.despawned").formatted(Formatting.YELLOW));
            return false;
        } else {
            CompanionSession ns = new CompanionSession(host);
            sessions.put(host.getUuid(), ns);
            boolean ok = ns.spawn();
            if (ok) host.sendMessage(Text.translatable("chat.quackingly.summoned").formatted(Formatting.AQUA));
            else host.sendMessage(Text.literal("Quackingly could not spawn (is Carpet installed?)").formatted(Formatting.RED));
            return ok;
        }
    }

    /** Send a chat message TO Quackingly on behalf of a player. */
    public void sendToCompanion(ServerPlayerEntity host, String message) {
        CompanionSession s = sessions.get(host.getUuid());
        if (s == null || !s.isAlive()) {
            host.sendMessage(Text.translatable("chat.quackingly.no_api_key").formatted(Formatting.RED));
            return;
        }
        s.handleUserMessage(host, message);
    }

    public CompanionSession getSession(ServerPlayerEntity host) {
        return sessions.get(host.getUuid());
    }

    /* ---- Per-player session ---- */

    public class CompanionSession {
        private final ServerPlayerEntity host;
        private EntityPlayerMPFake fakePlayer;
        private final ConversationMemory memory = new ConversationMemory();
        private LLMProvider llm;
        private String mode;

        public CompanionSession(ServerPlayerEntity host) {
            this.host = host;
            this.mode = QuackinglyConfig.get().defaultMode;
        }

        public boolean spawn() {
            if (server == null) return false;
            try {
                fakePlayer = EntityPlayerMPFake.createFake(
                        "Quackingly",
                        server,
                        host.getX() + 1, host.getY(), host.getZ() + 1,
                        host.getYaw(), 0f,
                        GameMode.SURVIVAL);
                if (fakePlayer == null) return false;
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

        public EntityPlayerMPFake getFakePlayer() { return fakePlayer; }

        public void setMode(String m) { this.mode = m; }
        public String getMode() { return mode; }

        public void handleUserMessage(ServerPlayerEntity from, String text) {
            memory.addUser("[" + from.getName().getString() + "] " + text);
            host.sendMessage(Text.literal("Quackingly is thinking...").formatted(Formatting.ITALIC, Formatting.GRAY));

            // Run LLM call off-thread to not block server tick
            server.execute(() -> {
                try {
                    if (llm == null) llm = LLMProvider.fromConfig();
                    if (!llm.isReady()) {
                        fakePlayer.sendMessage(Text.literal("(no API key set)").formatted(Formatting.RED));
                        return;
                    }
                    String ctx = describeWorld(from);
                    String prompt = PromptManager.systemPrompt(mode, from.getName().getString(), ctx);
                    String reply = llm.chat(memory.buildForCall(prompt));
                    memory.addAssistant(reply);
                    fakePlayer.sendMessage(Text.literal("<Quackingly> " + reply).formatted(Formatting.WHITE));
                } catch (Exception e) {
                    Quackingly.LOGGER.error("LLM call failed", e);
                    fakePlayer.sendMessage(Text.literal("(error: " + e.getMessage() + ")").formatted(Formatting.RED));
                }
            });
        }

        private String describeWorld(ServerPlayerEntity p) {
            try {
                return String.format("Near %s at (%.0f, %.0f, %.0f), %s, time %d",
                        p.world.getRegistryKey().getValue(),
                        p.getX(), p.getY(), p.getZ(),
                        p.world.isNight() ? "night" : "day",
                        p.world.getTimeOfDay() % 24000);
            } catch (Throwable t) {
                return "in Minecraft";
            }
        }
    }
}
