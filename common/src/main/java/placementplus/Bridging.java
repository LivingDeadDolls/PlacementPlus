package placementplus;

import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import placementplus.config.PlacementPlusConfig;

/**
 * Bridging: when the crosshair points at nothing within reach while holding a block, the first empty space along
 * the line of sight that touches a block becomes the target, so you can place against the side of the block you
 * stand on without sneaking to its edge. The block it would be placed against is outlined.
 */
public final class Bridging {
    private Bridging() {
    }

    /**
     * Stands in for the crosshair target. It clicks the supporting block so the vanilla outline shows it;
     * {@link Placement} turns it into a click on the empty space when it is used.
     */
    public static final class BridgeHitResult extends BlockHitResult {
        private final HitResult original;

        BridgeHitResult(Vec3 location, Direction face, BlockPos pos, HitResult original) {
            super(location, face, pos, false);
            this.original = original;
        }

        public HitResult original() {
            return original;
        }
    }

    /** Called right after vanilla has worked out the crosshair target. */
    public static void afterPick(Minecraft minecraft, float partialTick) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        HitResult hit = minecraft.hitResult;
        if (player == null || level == null || hit == null || hit.getType() != HitResult.Type.MISS
                || !PlacementPlusConfig.get().bridging || !player.mayBuild() || minecraft.getCameraEntity() != player
                || Hands.blockHand(player) == null) {
            return;
        }
        BridgeHitResult target = find(level, player, partialTick, hit);
        if (target != null) {
            minecraft.hitResult = target;
        }
    }

    /** Puts the real crosshair target back, so that attacking never hits the supporting block. */
    public static void restore(Minecraft minecraft) {
        if (minecraft.hitResult instanceof BridgeHitResult bridge) {
            minecraft.hitResult = bridge.original();
        }
    }

    /** Walks the line of sight one block space at a time (Amanatides-Woo) up to the reach distance. */
    private static BridgeHitResult find(ClientLevel level, LocalPlayer player, float partialTick, HitResult original) {
        Vec3 from = player.getEyePosition(partialTick);
        Vec3 dir = player.getViewVector(partialTick);
        double reach = player.blockInteractionRange();
        AABB body = player.getBoundingBox();
        Direction[] supportOrder = supportOrder(dir);

        int x = Mth.floor(from.x);
        int y = Mth.floor(from.y);
        int z = Mth.floor(from.z);
        int stepX = (int) Math.signum(dir.x);
        int stepY = (int) Math.signum(dir.y);
        int stepZ = (int) Math.signum(dir.z);
        double deltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dir.x);
        double deltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dir.y);
        double deltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dir.z);
        double maxX = stepX == 0 ? Double.MAX_VALUE : deltaX * (stepX > 0 ? x + 1 - from.x : from.x - x);
        double maxY = stepY == 0 ? Double.MAX_VALUE : deltaY * (stepY > 0 ? y + 1 - from.y : from.y - y);
        double maxZ = stepZ == 0 ? Double.MAX_VALUE : deltaZ * (stepZ > 0 ? z + 1 - from.z : from.z - z);
        double travelled = 0.0;

        while (true) {
            // Step first: the space the eyes are in is never a target.
            if (maxX <= maxY && maxX <= maxZ) {
                travelled = maxX;
                maxX += deltaX;
                x += stepX;
            } else if (maxY <= maxZ) {
                travelled = maxY;
                maxY += deltaY;
                y += stepY;
            } else {
                travelled = maxZ;
                maxZ += deltaZ;
                z += stepZ;
            }
            if (travelled > reach) {
                return null;
            }
            BlockPos cell = new BlockPos(x, y, z);
            if (isFree(level, body, cell)) {
                Direction support = support(level, cell, supportOrder);
                if (support != null) {
                    BlockPos supportPos = cell.relative(support);
                    Direction face = support.getOpposite();
                    return new BridgeHitResult(Placement.faceCenter(supportPos, face), face, supportPos, original);
                }
            }
        }
    }

    /** An empty space a block could go into, outside the player. */
    private static boolean isFree(ClientLevel level, AABB body, BlockPos cell) {
        return !level.isOutsideBuildHeight(cell)
                && level.getWorldBorder().isWithinBounds(cell)
                && !body.intersects(new AABB(cell))
                && level.getBlockState(cell).canBeReplaced();
    }

    /**
     * The order in which the blocks around an empty space are tried: below first, then the sides from the one
     * facing the player to the one facing away, and above last.
     */
    private static Direction[] supportOrder(Vec3 dir) {
        Direction[] sides = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        Arrays.sort(sides, Comparator.comparingDouble(side -> side.getUnitVec3().dot(dir)));
        return new Direction[] {Direction.DOWN, sides[0], sides[1], sides[2], sides[3], Direction.UP};
    }

    /** The side of {@code cell} with a block to place against, or null. */
    private static Direction support(ClientLevel level, BlockPos cell, Direction[] order) {
        for (Direction direction : order) {
            if (!level.getBlockState(cell.relative(direction)).canBeReplaced()) {
                return direction;
            }
        }
        return null;
    }
}
