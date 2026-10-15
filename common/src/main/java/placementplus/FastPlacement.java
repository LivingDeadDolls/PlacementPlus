package placementplus;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import placementplus.config.PlacementPlusConfig;

/**
 * Fast Placement: while the use key is held with a block, a block is placed each time the crosshair moves onto
 * another block, instead of every 4 ticks. The first block of each press is placed by vanilla; after that
 * vanilla's own repeat is held back and this takes over.
 *
 * <p>Two options build on it. Continuous Placement also keeps placing in front of the block just placed, without
 * the crosshair moving, up to a number of blocks per tick. Keep First Orientation places every block with the
 * side, click position and rotation of the first one.
 *
 * <p>Blocks placed here always click the empty space they go into, so the block in front is never used
 * (opened, flipped...) while sweeping across it.
 */
public final class FastPlacement {
    /** Ticks after the first block during which nothing more is placed, as vanilla's delay; a click may be ending. */
    private static final int COOLDOWN_TICKS = 4;
    /** How far the mouse must move, as a fraction of the window, to end that wait early. */
    private static final double COOLDOWN_MOUSE_MOVE = 0.1;
    /** How far the player must move to place against the block just placed again, without Continuous Placement. */
    private static final double STEP = 0.99;

    /** A click to send in place of the crosshair, with the rotation to look in (null to keep the player's own). */
    public record Click(BlockHitResult hit, Look look) {
    }

    private record Target(BlockPos clicked, BlockPos placePos, Placement.Adjusted adjusted) {
    }

    /** The click being sent right now, if Fast Placement sent it. */
    private static Click pending;
    private static boolean placing;
    private static int placedCount;

    // State since the use key was pressed. firstSide is null until the first block is placed.
    private static BlockPos firstPos;
    private static Direction firstSide;
    private static Vec3 firstOffset;
    /** The rotation the first block was placed with by Flexible Placement, or null if the player's own. */
    private static Look firstLook;
    /** The player's own rotation when the first block was placed. */
    private static Look firstPlayerLook;
    private static BlockPos lastPos;
    private static Vec3 lastPlayerPos;
    private static BlockPos lastSeen;
    private static int ticksSinceFirst;
    private static Vec3 firstMouse;
    private static final List<Click> backfill = new ArrayList<>();

    private FastPlacement() {
    }

    static Click pending() {
        return pending;
    }

    /** Called for every block placed by right-clicking, by vanilla or by us. */
    static void onPlaced(LocalPlayer player, BlockPos pos, BlockHitResult hit, Look look) {
        placedCount++;
        if (firstSide == null) {
            firstPos = pos;
            firstSide = hit.getDirection();
            firstOffset = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
            // The rotation it was placed with, so later blocks can face the same way.
            firstLook = look;
            firstPlayerLook = Look.of(player);
            ticksSinceFirst = 0;
            firstMouse = mouse(Minecraft.getInstance());
        }
        lastPos = pos;
        lastPlayerPos = player.position();
    }

    /** Whether vanilla's every-4-ticks repeat should be held back. Checked before vanilla handles the use key. */
    public static boolean suppressVanillaRepeat(Minecraft minecraft) {
        // Items Fast Placement does not work with keep vanilla's repeat.
        return isActive(minecraft) && firstSide != null && blockHand(minecraft.player) != null;
    }

    /** Called once a tick after vanilla has handled the use key. Runs Continuous Placement. */
    public static void tick(Minecraft minecraft, Runnable startUseItem, Runnable pick) {
        if (!isActive(minecraft)) {
            reset();
            return;
        }
        if (firstSide == null) {
            return;
        }
        ticksSinceFirst++;
        if (!coolingDown(minecraft) && !backfill.isEmpty()) {
            // The wait is over and the crosshair was swept meanwhile: fill in now, even if it has stopped since.
            placeBackfill(minecraft, startUseItem);
        }
        PlacementPlusConfig config = PlacementPlusConfig.get();
        if (!config.continuousPlacement || coolingDown(minecraft)) {
            return;
        }
        // Wherever the crosshair points now, even in front of the block just placed; look again after each block.
        for (int i = 0; i < config.fastPlacementCount; i++) {
            Target target = target(minecraft);
            if (target == null || target.placePos().equals(lastPos) || !place(minecraft, click(minecraft.player, target), startUseItem)) {
                return;
            }
            // Looking again runs afterPick, which must not place more than the count allows.
            placing = true;
            try {
                pick.run();
            } finally {
                placing = false;
            }
        }
    }

    /** Called each time vanilla works out the crosshair target, which is every frame. */
    public static void afterPick(Minecraft minecraft, Runnable startUseItem) {
        if (placing || firstSide == null || !isActive(minecraft) || minecraft.gui.screen() != null) {
            return;
        }
        Target target = target(minecraft);
        if (target == null) {
            return;
        }
        // A block once each time the crosshair moves onto another block. Without Continuous Placement, placing
        // against the block just placed needs a step first; otherwise it would run a line out on its own.
        BlockPos clicked = target.clicked();
        boolean fresh = !clicked.equals(lastSeen) && !clicked.equals(lastPos)
                || clicked.equals(lastPos) && hasStepped(minecraft.player, target.adjusted().hit().getDirection().getAxis());
        lastSeen = clicked;
        if (!fresh || target.placePos().equals(lastPos)) {
            return;
        }
        Click click = click(minecraft.player, target);
        if (coolingDown(minecraft)) {
            // Could still be the end of a single click; remember the spot in case the crosshair is being swept.
            backfill.add(click);
            return;
        }
        placeBackfill(minecraft, startUseItem);
        place(minecraft, click, startUseItem);
    }

    /** Places the spots swept over during the wait after the first click. */
    private static void placeBackfill(Minecraft minecraft, Runnable startUseItem) {
        List<Click> clicks = new ArrayList<>(backfill);
        backfill.clear();
        for (Click click : clicks) {
            place(minecraft, click, startUseItem);
        }
    }

    /** The click that places at {@code target}, on the empty space itself. */
    private static Click click(LocalPlayer player, Target target) {
        BlockPos pos = target.placePos();
        if (PlacementPlusConfig.get().keepFirstOrientation) {
            // Same side, click position and rotation as the first block, so every block is oriented alike. The
            // player's own first rotation is only sent when the way they face has changed since, since turning
            // sideways on the server costs a wait.
            Look look = firstLook != null ? firstLook : !Look.of(player).facesLike(firstPlayerLook) ? firstPlayerLook : null;
            return new Click(new BlockHitResult(Vec3.atLowerCornerOf(pos).add(firstOffset), firstSide, pos, false), look);
        }
        BlockHitResult hit = target.adjusted().hit();
        return new Click(new BlockHitResult(hit.getLocation(), hit.getDirection(), pos, false), target.adjusted().look());
    }

    private static boolean coolingDown(Minecraft minecraft) {
        return ticksSinceFirst < COOLDOWN_TICKS && !mouseMoved(minecraft);
    }

    private static boolean isActive(Minecraft minecraft) {
        // Not in Adventure mode: its blocks click the empty space, which Adventure mode does not allow.
        return PlacementPlusConfig.get().fastPlacement && minecraft.options.keyUse.isDown()
                && minecraft.player != null && minecraft.player.mayBuild();
    }

    private static void reset() {
        firstPos = null;
        firstSide = null;
        firstOffset = null;
        firstLook = null;
        firstPlayerLook = null;
        lastPos = null;
        lastPlayerPos = null;
        lastSeen = null;
        firstMouse = null;
        backfill.clear();
    }

    /** Where a block from the hand would go now, or null if none can be placed there. */
    private static Target target(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null || minecraft.level == null
                || player.isUsingItem() || player.isHandsBusy() || minecraft.gameMode.isDestroying()) {
            return null;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        InteractionHand hand = blockHand(player);
        if (hand == null) {
            return null;
        }
        ItemStack stack = player.getItemInHand(hand);
        Placement.Adjusted adjusted = Placement.adjust(player, hand, stack, hit);
        BlockPlaceContext context = new BlockPlaceContext(player, hand, stack, adjusted.hit());
        BlockPos pos = context.getClickedPos();
        if (!context.canPlace()) {
            return null;
        }
        if (firstPos != null) {
            PlacementPlusConfig config = PlacementPlusConfig.get();
            if (!config.placementRestriction.allows(pos, adjusted.hit().getDirection(), firstPos, firstSide)
                    || !PlacementRestriction.onGrid(pos, firstPos, config.placementGrid)) {
                return null;
            }
        }
        return new Target(hit.getBlockPos(), pos, adjusted);
    }

    /**
     * The hand with a block Fast Placement works with: the main hand, or the off hand when the main hand is empty
     * (the empty main hand is tried first and does nothing, since the click is on an empty space). Null if none.
     */
    private static InteractionHand blockHand(LocalPlayer player) {
        InteractionHand hand = Hands.blockHand(player);
        return hand != null && PlacementPlusConfig.get().allowsFastPlacement(player.getItemInHand(hand).getItem()) ? hand : null;
    }

    /** Sends {@code click} through vanilla's right-click handling. Returns whether a block was placed. */
    static boolean place(Minecraft minecraft, Click click, Runnable startUseItem) {
        HitResult crosshair = minecraft.hitResult;
        int before = placedCount;
        placing = true;
        pending = click;
        minecraft.hitResult = click.hit();
        try {
            startUseItem.run();
        } finally {
            minecraft.hitResult = crosshair;
            pending = null;
            placing = false;
        }
        return placedCount != before;
    }

    private static boolean hasStepped(LocalPlayer player, Direction.Axis axis) {
        if (lastPlayerPos == null) {
            return false;
        }
        Vec3 now = player.position();
        return Math.abs(axis.choose(now.x, now.y, now.z) - axis.choose(lastPlayerPos.x, lastPlayerPos.y, lastPlayerPos.z)) >= STEP;
    }

    /** The mouse position as a fraction of the window, or null when the window has no size. */
    private static Vec3 mouse(Minecraft minecraft) {
        int width = minecraft.getWindow().getScreenWidth();
        int height = minecraft.getWindow().getScreenHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        return new Vec3(minecraft.mouseHandler.xpos() / width, minecraft.mouseHandler.ypos() / height, 0.0);
    }

    private static boolean mouseMoved(Minecraft minecraft) {
        Vec3 now = mouse(minecraft);
        return now != null && firstMouse != null && now.distanceTo(firstMouse) >= COOLDOWN_MOUSE_MOVE;
    }
}
