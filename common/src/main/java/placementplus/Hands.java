package placementplus;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Which hand a block is placed from. */
public final class Hands {
    private Hands() {
    }

    /**
     * The hand holding the block a right-click places: the main hand, or the off hand when the main hand is
     * empty. Null otherwise; with something else in the main hand, that item would be used first.
     */
    public static InteractionHand blockHand(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof BlockItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (main.isEmpty() && player.getOffhandItem().getItem() instanceof BlockItem) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }
}
