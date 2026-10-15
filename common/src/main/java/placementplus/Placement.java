package placementplus;

import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import placementplus.config.PlacementPlusConfig;

/**
 * Rewrites a right click on a block before it is sent, for Flexible Placement, Fake Sneak and Bridging.
 * Everything here only changes which block face is clicked and which way the player looks while clicking,
 * so the server sees an ordinary click and nothing needs to be installed there.
 */
public final class Placement {
    /** Half the width of the center square of a block face; outside it, the click is on one of the four edges. */
    private static final double CENTER_HALF_WIDTH = 0.25;

    private Placement() {
    }

    /**
     * The click to send instead of the original one.
     *
     * @param hit  the block face to click
     * @param look the rotation to look in while clicking, or null to keep the player's own
     */
    public record Adjusted(BlockHitResult hit, Look look) {
    }

    public static Adjusted adjust(LocalPlayer player, InteractionHand hand, ItemStack stack, BlockHitResult hit) {
        boolean bridge = hit instanceof Bridging.BridgeHitResult;
        if (!(stack.getItem() instanceof BlockItem)) {
            // Bridging clicks a block next to an empty space; with anything but a block in hand, click the
            // empty space itself so the neighbouring block (a door, a chest...) is not used by accident.
            return new Adjusted(bridge ? clickInto(hit) : hit, null);
        }
        PlacementPlusConfig config = PlacementPlusConfig.get();
        Look look = null;
        if (config.flexiblePlacement) {
            Direction edge = edge(hit);
            if (Keys.rotationDown()) {
                // The center of a face reverses the usual direction: as if looking out of the block, not into it.
                look = Look.toward(edge != null ? edge : hit.getDirection(), player);
            }
            if (Keys.adjacentDown() && edge != null) {
                // Click the side of the same block instead, so the new block goes next to it.
                BlockPos pos = hit.getBlockPos();
                hit = new BlockHitResult(faceCenter(pos, edge), edge, pos, false);
            }
        }
        if (!player.mayBuild()) {
            // In Adventure mode a block may only be placed by clicking a block it allows, so the click stays on
            // the block actually clicked; the rest below clicks somewhere else. Bridging does not run there.
            return new Adjusted(hit, look);
        }
        boolean flexibleKeyHeld = config.flexiblePlacement && (Keys.rotationDown() || Keys.adjacentDown());
        if (config.scaffoldPlacement && !bridge && !flexibleKeyHeld) {
            BlockHitResult extended = scaffold(player, stack, hit);
            if (extended != null) {
                return new Adjusted(extended, null);
            }
        }
        if (bridge) {
            hit = clickInto(hit);
        } else if (config.fakeSneak && !player.isSecondaryUseActive()) {
            BlockPlaceContext context = new BlockPlaceContext(player, hand, stack, hit);
            // Only when the block would go in front of the clicked one; leave slabs being doubled and the like alone.
            if (context.canPlace() && !context.replacingClickedOnBlock()) {
                hit = clickInto(hit);
            }
        }
        return new Adjusted(hit, look);
    }

    /**
     * Sends the click returned by {@link #adjust} through {@code useItemOn}, which performs the click.
     * Successful block placements are reported to {@link FastPlacement}.
     */
    public static InteractionResult useItemOn(ClientPacketListener connection, LocalPlayer player, InteractionHand hand,
                                              BlockHitResult hit, Function<BlockHitResult, InteractionResult> useItemOn) {
        ItemStack stack = player.getItemInHand(hand);
        boolean block = stack.getItem() instanceof BlockItem;
        FastPlacement.Click pending = FastPlacement.pending();
        // A click from Fast Placement is already worked out. Its rotation is only for placing the block, not
        // for an empty main hand that vanilla tries first.
        Adjusted adjusted = pending != null && pending.hit() == hit
                ? new Adjusted(hit, block ? pending.look() : null)
                : adjust(player, hand, stack, hit);
        // The server may still be turned for an earlier click; then this one is turned to the player's own
        // rotation. That is not a rotation of this click's own, so Fast Placement is not told about it.
        Look look = block && adjusted.look() == null && ServerLook.isTurned() ? Look.of(player) : adjusted.look();
        BlockHitResult click = adjusted.hit();
        if (look != null && ServerLook.mustWait(player, look)) {
            // Turn on the server first; the click follows once the head has turned. Fail stops vanilla here.
            ServerLook.holdBack(connection, player, new FastPlacement.Click(click, adjusted.look()), look);
            return InteractionResult.FAIL;
        }
        // Worked out before the click, which may use up the stack.
        BlockPos placedAt = block ? new BlockPlaceContext(player, hand, stack, click).getClickedPos() : null;
        BlockState before = placedAt != null ? player.level().getBlockState(placedAt) : null;
        InteractionResult result = look == null
                ? useItemOn.apply(click)
                : withLook(connection, player, look, () -> useItemOn.apply(click));
        // Success may also mean the clicked block was used (a door opened...); only a changed block was placed.
        if (placedAt != null && result instanceof InteractionResult.Success && player.level().getBlockState(placedAt) != before) {
            FastPlacement.onPlaced(player, placedAt, click, adjusted.look());
        }
        return result;
    }

    /**
     * Turns the player to {@code look} for the duration of {@code action}. The server is turned before the click,
     * since blocks take their facing from the rotation it has for the player then; {@link ServerLook} turns it
     * back at the end of the tick.
     */
    private static <T> T withLook(ClientPacketListener connection, LocalPlayer player, Look look, Supplier<T> action) {
        float yRot = player.getYRot();
        float xRot = player.getXRot();
        float yHeadRot = player.getYHeadRot();
        ServerLook.turn(connection, player, look);
        // The head too, for blocks that face by it, so the block placed here matches the server's.
        player.setYRot(look.yRot());
        player.setXRot(look.xRot());
        player.setYHeadRot(look.yRot());
        try {
            return action.get();
        } finally {
            player.setYRot(yRot);
            player.setXRot(xRot);
            player.setYHeadRot(yHeadRot);
        }
    }

    /**
     * Scaffold Placement: clicking the same block as the one in hand extends the run of that block, like
     * scaffolding. The run goes toward the edge of the face aimed at; from the center, it goes the way the player
     * faces when clicking the top or bottom, and up when clicking a side. Returns a click on the first free space
     * along the run, or null to place as usual.
     */
    private static BlockHitResult scaffold(LocalPlayer player, ItemStack stack, BlockHitResult hit) {
        Block block = ((BlockItem) stack.getItem()).getBlock();
        Level level = player.level();
        BlockPos start = hit.getBlockPos();
        if (!level.getBlockState(start).is(block)) {
            return null;
        }
        Direction edge = edge(hit);
        Direction extend = edge != null ? edge
                : hit.getDirection().getAxis() == Direction.Axis.Y ? player.getDirection() : Direction.UP;
        BlockPos from = start;
        for (int i = 0; i < PlacementPlusConfig.get().scaffoldDistance; i++) {
            BlockPos pos = from.relative(extend);
            if (!player.isWithinBlockInteractionRange(pos, 0.0)) {
                return null;
            }
            BlockState state = level.getBlockState(pos);
            if (state.canBeReplaced()) {
                return new BlockHitResult(faceCenter(from, extend), extend, pos, false);
            }
            if (!state.is(block)) {
                return null;
            }
            from = pos;
        }
        return null;
    }

    /**
     * Which edge of the clicked face the hit is near, as the direction from the face's center toward it,
     * or null when the hit is in the center square.
     */
    static Direction edge(BlockHitResult hit) {
        Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos()));
        Direction.Axis faceAxis = hit.getDirection().getAxis();
        Direction.Axis best = null;
        double bestOffset = 0.0;
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis == faceAxis) {
                continue;
            }
            double offset = axis.choose(local.x, local.y, local.z) - 0.5;
            if (best == null || Math.abs(offset) > Math.abs(bestOffset)) {
                best = axis;
                bestOffset = offset;
            }
        }
        if (Math.abs(bestOffset) < CENTER_HALF_WIDTH) {
            return null;
        }
        return Direction.fromAxisAndDirection(best,
                bestOffset > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
    }

    /**
     * The same placement, but clicking the empty space the block will go into rather than the block in front of it.
     * The clicked block's own action (opening a chest, flipping a lever...) is then never triggered.
     */
    static BlockHitResult clickInto(BlockHitResult hit) {
        return new BlockHitResult(hit.getLocation(), hit.getDirection(), hit.getBlockPos().relative(hit.getDirection()), false);
    }

    static Vec3 faceCenter(BlockPos pos, Direction face) {
        return Vec3.atCenterOf(pos).add(face.getUnitVec3().scale(0.5));
    }
}
