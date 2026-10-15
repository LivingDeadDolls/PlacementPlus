package placementplus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import placementplus.Look;
import placementplus.ServerLook;

@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    /**
     * While the server is turned for a placement, vanilla's movement packets carry that rotation too; otherwise
     * moving the mouse would send the real one in between and undo the turn before the click arrives.
     * Before 26.3 there is no sendChanges: tick() sends the riding rotation itself (below) and calls sendPosition.
     */
    @WrapMethod(method = "sendPosition")
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

    /** The rotation sent while riding, which does not go through sendPosition. */
    @WrapOperation(method = "tick", at = @At(value = "NEW", target = "net/minecraft/network/protocol/game/ServerboundMovePlayerPacket$Rot"))
    private ServerboundMovePlayerPacket.Rot placementplus$keepTurnedRiding(float yRot, float xRot, boolean onGround,
            boolean horizontalCollision, Operation<ServerboundMovePlayerPacket.Rot> original) {
        Look look = ServerLook.rotationToSend();
        return look == null ? original.call(yRot, xRot, onGround, horizontalCollision)
            : original.call(look.yRot(), look.xRot(), onGround, horizontalCollision);
    }
}
