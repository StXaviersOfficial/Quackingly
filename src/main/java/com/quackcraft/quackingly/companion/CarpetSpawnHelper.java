package com.quackcraft.quackingly.companion;

import carpet.CarpetSettings;
import carpet.patches.EntityPlayerMPFake;
import com.quackcraft.quackingly.Quackingly;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * Carpet-dependent spawn logic. This class is only loaded when Carpet is present
 * (the caller checks FabricLoader.isModLoaded("carpet") first). If Carpet is
 * missing, this class never loads and we never get NoClassDefFoundError.
 */
public final class CarpetSpawnHelper {

    private CarpetSpawnHelper() {}

    /** Returns the spawned EntityPlayerMPFake, or null on failure. */
    public static Object spawnFakePlayer(MinecraftServer server, ServerPlayerEntity host) {
        try {
            CarpetSettings.allowSpawningOfflinePlayers = true;

            Vec3d pos = new Vec3d(host.getX() + 1, host.getY(), host.getZ() + 1);
            EntityPlayerMPFake.createFake(
                    "Quackingly",
                    server,
                    pos,
                    host.getYaw(), 0f,
                    host.getWorld().getRegistryKey(),
                    GameMode.SURVIVAL,
                    false);

            // Look up the freshly-spawned fake player by name (createFake returns void)
            return server.getPlayerManager().getPlayer("Quackingly");
        } catch (Throwable t) {
            Quackingly.LOGGER.error("Carpet fake-player spawn failed", t);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends net.minecraft.entity.player.PlayerEntity> fakePlayerClass() {
        return EntityPlayerMPFake.class;
    }
}
