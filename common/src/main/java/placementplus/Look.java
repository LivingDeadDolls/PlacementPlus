package placementplus;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** A rotation to look in while clicking, in degrees as the player's own yRot and xRot. */
public record Look(float yRot, float xRot) {
    /** Looking straight toward {@code direction}; straight up or down keeps the player's current yRot. */
    public static Look toward(Direction direction, LocalPlayer player) {
        float yRot = direction.getAxis().isHorizontal() ? direction.toYRot() : player.getYRot();
        float xRot = direction == Direction.UP ? -90.0F : direction == Direction.DOWN ? 90.0F : 0.0F;
        return new Look(yRot, xRot);
    }

    /** The way the player looks now. */
    public static Look of(LocalPlayer player) {
        return new Look(player.getYRot(), player.getXRot());
    }

    /**
     * Whether a block placed looking this way faces the same as looking {@code other} way: by the horizontal
     * direction (stairs, doors...) and by the nearest direction (pistons, observers...).
     */
    public boolean facesLike(Look other) {
        return Direction.fromYRot(yRot) == Direction.fromYRot(other.yRot) && nearest() == other.nearest();
    }

    /** The same pitch with another yaw. */
    public Look withYRot(float yRot) {
        return new Look(yRot, xRot);
    }

    private Direction nearest() {
        return Direction.getApproximateNearest(Vec3.directionFromRotation(xRot, yRot));
    }
}
