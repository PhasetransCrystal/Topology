package net.ptcrys.topo.data.machine.common.component.resource;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Delivers a {@link MachineDestroyedReport} to the scene: broadcasts its chat line to every player
 * within {@link #BROADCAST_RADIUS} of the destroyed machine, and on {@code HARMFUL} severity gives
 * each of those players a gentle radial shove (knockback only, no damage — the tier's own world
 * effect already dealt the harm). Runs inside the {@code preRemoveSideEffects} window on the server
 * thread. The "what happened" lives in the report; this class owns only "who hears/feels it".
 */
public final class MachineDestroyedFeedback {

    /** Players within this many blocks of the machine receive the chat line and (if harmful) the shove. */
    public static final double BROADCAST_RADIUS = 16.0;

    /** Radial knockback strength applied to each in-range player on harmful destruction. */
    public static final double SHOCKWAVE_KNOCKBACK = 0.5;

    private static final double RADIUS_SQR = BROADCAST_RADIUS * BROADCAST_RADIUS;

    private MachineDestroyedFeedback() {}

    /**
     * Broadcasts {@code message} (if any) to every server player within {@link #BROADCAST_RADIUS} of
     * {@code pos}, and shoves them radially outward when {@code severity} is harmful. No-op for a
     * silent report or when no players are nearby.
     */
    public static void deliver(
                               ServerLevel level, BlockPos pos, MachineDestroyedReport.Severity severity, @Nullable Component message) {
        if (severity == MachineDestroyedReport.Severity.SILENT) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        AABB box = AABB.ofSize(center, 2.0 * BROADCAST_RADIUS, 2.0 * BROADCAST_RADIUS, 2.0 * BROADCAST_RADIUS);
        boolean shockwave = shouldShockwave(severity);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box, p -> withinRadius(center, p.position()))) {
            if (message != null) {
                player.sendSystemMessage(message);
            }
            if (shockwave) {
                applyShockwave(player, center);
            }
        }
    }

    private static void applyShockwave(ServerPlayer player, Vec3 center) {
        Vec3 impulse = radialKnockback(center, player.position(), SHOCKWAVE_KNOCKBACK);
        if (impulse.lengthSqr() == 0.0) {
            return;
        }
        player.push(impulse.x, impulse.y, impulse.z);
        // Players self-manage their velocity, so the server must mark the motion dirty to push a packet.
        player.hurtMarked = true;
    }

    /** Only harmful destruction (arc / blast / heat flash) shoves nearby players. */
    static boolean shouldShockwave(MachineDestroyedReport.Severity severity) {
        return severity == MachineDestroyedReport.Severity.HARMFUL;
    }

    /** Whether {@code target} lies within the feedback radius of {@code center} (inclusive). */
    static boolean withinRadius(Vec3 center, Vec3 target) {
        return target.distanceToSqr(center) <= RADIUS_SQR;
    }

    /**
     * Horizontal impulse of magnitude {@code strength} pointing from {@code center} toward
     * {@code target}; {@link Vec3#ZERO} when they share a column (no outward direction).
     */
    static Vec3 radialKnockback(Vec3 center, Vec3 target, double strength) {
        double dx = target.x - center.x;
        double dz = target.z - center.z;
        double horizontalSqr = dx * dx + dz * dz;
        if (horizontalSqr < 1.0e-8) {
            return Vec3.ZERO;
        }
        double scale = strength / Math.sqrt(horizontalSqr);
        return new Vec3(dx * scale, 0.0, dz * scale);
    }
}
