package com.quackcraft.quackingly.companion;

import com.quackcraft.quackingly.Quackingly;
import com.quackcraft.quackingly.config.QuackinglyConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Tick-based brain for the Quackingly companion.
 *
 * Every server tick we:
 *   1. Find the host player's session.
 *   2. If the companion is in a different dimension or very far (>32 blocks), teleport.
 *   3. Otherwise, walk toward the host using velocity (smooth, no teleport jerks).
 *   4. If close (<4 blocks), stop and look at the host.
 *
 * Why manual velocity instead of EntityNavigation:
 *   Carpet's EntityPlayerMPFake extends ServerPlayerEntity, which is NOT a
 *   PathAwareEntity. So we can't use mob-style pathfinding on it. Instead,
 *   we set the velocity vector toward the host each tick — MC's physics
 *   handles the rest (collision, gravity, jumping). This gives smooth,
 *   collision-aware movement without teleport jerks.
 *
 * Token optimisation: this is all tick-based and local — no LLM calls.
 */
public final class CompanionBrain {

    private CompanionBrain() {}

    private static final double FOLLOW_STOP  = 4.0;
    private static final double TELEPORT_DIST = 32.0;
    private static final double WALK_SPEED = 0.25;
    private static final double SPRINT_SPEED = 0.35;

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
            try {
                companion.teleport(
                        host.getServerWorld(),
                        host.getX(), host.getY(), host.getZ(),
                        java.util.Set.of(),
                        host.getYaw(), host.getPitch());
            } catch (Throwable t) {
                Quackingly.LOGGER.debug("[Quackingly] cross-dimension teleport failed: {}", t.toString());
            }
            return;
        }

        Vec3d cPos = companion.getPos();
        Vec3d hPos = host.getPos();
        double dist = cPos.distanceTo(hPos);

        // Too far — teleport behind host (avoids long pathfinding)
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
            walkToward(companion, hPos, dist);
        } else {
            // Close enough — stop moving
            companion.setVelocity(0, companion.getVelocity().y, 0);
            companion.velocityModified = true;
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
        // Smoothly interpolate by setting directly; MC will handle client-side smoothing
        companion.setYaw(yaw);
        companion.setHeadYaw(yaw);
        companion.setPitch(pitch);
    }

    private static void walkToward(PlayerEntity companion, Vec3d target, double dist) {
        Vec3d pos = companion.getPos();
        double dx = target.x - pos.x;
        double dz = target.z - pos.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.1) return;

        // Sprint if far, walk if closer — feels more natural
        boolean shouldSprint = dist > 10.0;
        double speed = shouldSprint ? SPRINT_SPEED : WALK_SPEED;
        double vx = (dx / len) * speed;
        double vz = (dz / len) * speed;
        companion.setVelocity(vx, companion.getVelocity().y, vz);
        companion.velocityModified = true;
        companion.setSprinting(shouldSprint);
        companion.setSneaking(false);

        // If we're hitting a wall (small horizontal movement), try to jump
        Vec3d curVel = companion.getVelocity();
        if (Math.abs(curVel.x) < 0.05 && Math.abs(curVel.z) < 0.05
                && companion.isOnGround()) {
            companion.setVelocity(curVel.x, 0.42, curVel.z);  // jump
            companion.velocityModified = true;
        }
    }
}
