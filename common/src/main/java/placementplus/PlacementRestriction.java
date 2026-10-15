package placementplus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import placementplus.config.PlacementPlusConfig;

/**
 * Where Fast Placement may place after the first block of a press, relative to that block ({@code first}) and the
 * side it was placed against ({@code firstSide}). The "plane" is the one through the first block across that side.
 */
public enum PlacementRestriction {
    /** Anywhere. */
    NONE,
    /** In the plane. */
    PLANE,
    /** Against the same side as the first block, anywhere. */
    FACE,
    /** In the column through the first block, straight out from the side. */
    COLUMN,
    /** In the plane, in line with the first block along either direction. */
    LINE,
    /** In the layers above (or below, for a negative height) the first block, counting it. */
    LAYER,
    /** In the plane, on the diagonals through the first block. */
    DIAGONAL;

    public boolean allows(BlockPos pos, Direction side, BlockPos first, Direction firstSide) {
        Direction.Axis axis = firstSide.getAxis();
        int dx = pos.getX() - first.getX();
        int dy = pos.getY() - first.getY();
        int dz = pos.getZ() - first.getZ();
        // Offset across the plane, and the two offsets within it.
        int across = axis.choose(dx, dy, dz);
        int u = axis == Direction.Axis.X ? dy : dx;
        int v = axis == Direction.Axis.Z ? dy : dz;
        return switch (this) {
            case NONE -> true;
            case PLANE -> across == 0;
            case FACE -> side == firstSide;
            case COLUMN -> u == 0 && v == 0;
            case LINE -> across == 0 && (u == 0 || v == 0);
            case LAYER -> {
                int height = PlacementPlusConfig.get().restrictionLayerHeight;
                if (height > 0) {
                    yield dy >= 0 && dy < height;
                }
                if (height < 0) {
                    yield dy <= 0 && -dy < -height;
                }
                yield true;
            }
            case DIAGONAL -> across == 0 && Math.abs(u) == Math.abs(v);
        };
    }

    /** Whether {@code pos} is on the grid of the given spacing that goes through {@code first}. */
    public static boolean onGrid(BlockPos pos, BlockPos first, int spacing) {
        return spacing <= 1
                || (pos.getX() - first.getX()) % spacing == 0
                && (pos.getY() - first.getY()) % spacing == 0
                && (pos.getZ() - first.getZ()) % spacing == 0;
    }
}
