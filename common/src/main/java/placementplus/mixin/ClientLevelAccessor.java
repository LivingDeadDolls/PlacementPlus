package placementplus.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {
    /** Numbers block-changing packets, as vanilla does for its clicks. */
    @Invoker("getBlockStatePredictionHandler")
    BlockStatePredictionHandler placementplus$predictions();
}
