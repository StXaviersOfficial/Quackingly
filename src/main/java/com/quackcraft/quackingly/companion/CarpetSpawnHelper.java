package com.quackcraft.quackingly.companion;

import carpet.CarpetSettings;
import carpet.patches.EntityPlayerMPFake;
import com.quackcraft.quackingly.Quackingly;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * Carpet-dependent spawn logic. This class is only loaded when Carpet is present.
 *
 * IMPORTANT: Carpet lowercases the fake-player name ("Quackingly" → "quackingly")
 * and the join happens ASYNC (on the network thread). So after calling createFake,
 * we can't immediately look up the player — we need to retry with a delay.
 *
 * createFake MUST be called on the server thread (it modifies server state).
 * lookupFakePlayer can be called from any thread (it just reads the player list).
 */
public final class CarpetSpawnHelper {

    public static final String FAKE_PLAYER_NAME = "Quackingly";
    private static final int MAX_LOOKUP_RETRIES = 40;   // 40 × 50ms = 2 seconds max wait
    private static final long RETRY_DELAY_MS = 50;

    private CarpetSpawnHelper() {}

    /**
     * Tell Carpet to create the fake player. MUST be called on the server thread.
     * The player won't be immediately available — use lookupFakePlayer() to wait for it.
     */
    public static void createFakePlayer(MinecraftServer server, ServerPlayerEntity host) {
        CarpetSettings.allowSpawningOfflinePlayers = true;
        // Spawn at host's EXACT position (not +1 offset which could be over a hole/void)
        Vec3d pos = new Vec3d(host.getX(), host.getY(), host.getZ());
        EntityPlayerMPFake.createFake(
                FAKE_PLAYER_NAME,
                server,
                pos,
                host.getYaw(), 0f,
                host.getWorld().getRegistryKey(),
                GameMode.CREATIVE,  // Creative so he doesn't die to mobs
                false);
    }

    /**
     * Look up the fake player by name (case-insensitive, with retries).
     * Can be called from any thread. Returns null if not found after all retries.
     */
    public static ServerPlayerEntity lookupFakePlayer(MinecraftServer server) {
        for (int i = 0; i < MAX_LOOKUP_RETRIES; i++) {
            // Exact match first (fast path)
            ServerPlayerEntity found = server.getPlayerManager().getPlayer(FAKE_PLAYER_NAME);
            if (found != null) {
                Quackingly.LOGGER.info("[Quackingly] Fake player found after {} retries.", i);
                return found;
            }
            // Case-insensitive fallback (Carpet lowercases the name)
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p.getName().getString().equalsIgnoreCase(FAKE_PLAYER_NAME)) {
                    Quackingly.LOGGER.info("[Quackingly] Fake player found (case-insensitive) after {} retries.", i);
                    return p;
                }
            }
            try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        Quackingly.LOGGER.error("[Quackingly] Fake player '{}' not found after {} retries.",
                FAKE_PLAYER_NAME, MAX_LOOKUP_RETRIES);
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends net.minecraft.entity.player.PlayerEntity> fakePlayerClass() {
        return EntityPlayerMPFake.class;
    }
}
