package placementplus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import placementplus.config.PlacementPlusConfig;

/**
 * Placement Preview: when Place Beside, Scaffold Placement or Bridging would put the block somewhere other than in
 * front of the block aimed at, the block outline is moved to where it will actually go. Otherwise vanilla's outline
 * is left as it is.
 */
public final class Preview {
    private Preview() {
    }

    /**
     * Where to draw the block outline instead of around the block aimed at, or null for vanilla's. Called each frame
     * while the outline is worked out.
     */
    public static BlockPos outlinePos(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (!PlacementPlusConfig.get().placementPreview || player == null || minecraft.level == null
                || player.isSpectator() || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        InteractionHand hand = Hands.blockHand(player);
        if (hand == null) {
            return null;
        }
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult click = Placement.adjust(player, hand, stack, hit).hit();
        BlockPlaceContext context = new BlockPlaceContext(player, hand, stack, click);
        // As vanilla checks before placing: Adventure mode only places where the item allows it.
        boolean allowed = player.mayBuild()
                || stack.canPlaceOnBlockInAdventureMode(new BlockInWorld(minecraft.level, click.getBlockPos(), false));
        if (!allowed || !context.canPlace()) {
            return null;
        }
        BlockPos pos = context.getClickedPos();
        // Bridging aims at the block it builds from, so its outline is always moved to the empty space.
        boolean moved = hit instanceof Bridging.BridgeHitResult
                || !pos.equals(new BlockPlaceContext(player, hand, stack, hit).getClickedPos());
        return moved ? pos : null;
    }
}
