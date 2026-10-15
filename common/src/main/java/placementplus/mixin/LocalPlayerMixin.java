package placementplus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import placementplus.Look;
import placementplus.ServerLook;

@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    /**
     * While the server is turned for a placement, vanilla's movement packets (also the rotation sent while riding)
     * carry that rotation too; otherwise moving the mouse would send the real one in between and undo the turn
     * before the click arrives.
     */
    @WrapMethod(method = "sendChanges")
    private void placementplus$keepTurned(Operation<Void> original) {
        Look look = ServerLook.rotationToSend();
        if (look == null) {
            original.call();
            return;
        }
        LocalPlayer player = (LocalPlayer) (Object) this;
        float yRot = player.getYRot();
        float xRot = player.getXRot();
        player.setYRot(look.yRot());
        player.setXRot(look.xRot());
        try {
            original.call();
        } finally {
            player.setYRot(yRot);
            player.setXRot(xRot);
        }
    }
}
