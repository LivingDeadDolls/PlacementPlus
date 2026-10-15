package placementplus.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import placementplus.ServerLook;

@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    /** The server acknowledges block-changing packets at the start of each player tick; see {@link ServerLook}. */
    @Inject(method = "handleBlockChangedAck", at = @At("HEAD"))
    private void placementplus$acknowledged(int sequence, CallbackInfo ci) {
        ServerLook.onAcknowledged((ClientLevel) (Object) this, sequence);
    }
}
