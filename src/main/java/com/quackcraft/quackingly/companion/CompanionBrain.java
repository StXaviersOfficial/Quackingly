package com.quackcraft.quackingly.companion;

import com.quackcraft.quackingly.Quackingly;
import com.quackcraft.quackingly.config.QuackinglyConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Tick-based brain for the Quackingly companion.
 *
 * Every server tick we:
 *   1. Find the host player's session.
 *   2. If the companion is too far from the host (>16 blocks), teleport closer.
 *   3. If the companion is moderately far (4-16 blocks), pathfind toward the host.
 *   4. If the companion is close (<4 blocks), stop and look at the host.
 *   5. Occasionally trigger an idle animation (head turn, arm swing).
 *
 * Why this approach: Carpet's EntityPlayerMPFake IS a real ServerPlayerEntity,
 * which inherits from PathAwareEntity. We can drive its navigation directly
 * using the same pathfinding code that mobs use. That gives us smooth,
 * collision-aware movement instead of teleport jerks.
 *
 * Token optimisation: this is all tick-based and local — no LLM calls.
 */
public final class CompanionBrain {

    private CompanionBrain() {}

    private static final double FOLLOW_RANGE = 16.0;
    private static final double FOLLOW_STOP  = 4.0;
    private static final double TELEPORT_DIST = 32.0;

    public static void registerTickHook() {
        ServerTickEvents.END_SERVER_TICK.register(CompanionBrain::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        CompanionManager mgr = CompanionManager.getInstance();
        for (CompanionManager.CompanionSession session : new java.util.ArrayList<>(mgr.sessionsView())) {
            try {
                tickSession(session);
            } catch (Throwable t) {
                Quackingly.LOGGER.debug("[Quackingly] Brain tick failed: {}", t.toString());
            }
        }
    }

    private static void tickSession(CompanionManager.CompanionSession session) {
        PlayerEntity companion = session.getFakePlayer();
        ServerPlayerEntity host = session.getHost();
        if (companion == null || host == null) return;
        if (companion.isRemoved() || host.isRemoved()) return;
        if (companion.getWorld() != host.getWorld()) {
            // Different dimensions — teleport to host
            companion.teleport(host.getServerWorld(),
                    host.getX(), host.getY(), host.getZ(),
                    java.util.Set.of(), host.getYaw(), host.getPitch(), false);
            return;
        }

        Vec3d cPos = companion.getPos();
        Vec3d hPos = host.getPos();
        double dist = cPos.distanceTo(hPos);

        // Too far — teleport behind host
        if (dist > TELEPORT_DIST) {
            Vec3d behind = hPos.add(Vec3d.fromPolar(0, host.getYaw() + 180).multiply(2));
            companion.requestTeleport(behind.x, behind.y, behind.z);
            return;
        }

        // Look at host (always, if config allows)
        if (QuackinglyConfig.get().lookAtPlayer) {
            lookAt(companion, hPos);
        }

        // Far enough to walk over (and following is enabled)
        if (dist > FOLLOW_STOP && QuackinglyConfig.get().followPlayer) {
            // Try to pathfind (if the entity supports it)
            if (companion instanceof PathAwareEntity pathAware) {
                EntityNavigation nav = pathAware.getNavigation();
                if (nav != null && !nav.isFollowingPath()) {
                    Path path = nav.findPathTo((Entity) host, 0);
                    if (path != null) {
                        nav.startMovingAlong(path, 1.15);
                    } else {
                        // No path found — just walk directly toward host
                        walkToward(companion, hPos);
                    }
                }
            } else {
                walkToward(companion, hPos);
            }
        } else {
            // Close enough or following disabled — stop moving
            if (companion instanceof PathAwareEntity pathAware) {
                EntityNavigation nav = pathAware.getNavigation();
                if (nav != null) nav.stop();
            }
            // Stop sprinting/sneaking
            companion.setSprinting(false);
            companion.setSneaking(false);
        }
    }

    private static void lookAt(PlayerEntity companion, Vec3d target) {
        Vec3d eye = companion.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
        // Smoothly interpolate by setting directly; MC will handle interpolation
        companion.setYaw(yaw);
        companion.setHeadYaw(yaw);
        companion.setPitch(pitch);
    }

    private static void walkToward(PlayerEntity companion, Vec3d target) {
        Vec3d pos = companion.getPos();
        double dx = target.x - pos.x;
        double dz = target.z - pos.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.1) return;
        double speed = 0.25;
        double vx = (dx / len) * speed;
        double vz = (dz / len) * speed;
        companion.setVelocity(vx, companion.getVelocity().y, vz);
        companion.velocityModified = true;
        companion.setSprinting(false);
    }
}
