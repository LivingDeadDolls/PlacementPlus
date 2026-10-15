package placementplus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import placementplus.Bridging;
import placementplus.FastPlacement;
import placementplus.Placement;
import placementplus.ServerLook;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Shadow
    protected abstract void startUseItem();

    @Shadow
    protected abstract void pick(float partialTick);

    @Shadow
    public abstract ClientPacketListener getConnection();

    @Shadow
    private int rightClickDelay;

    /**
     * Bridging: target an empty space when the crosshair points at nothing.
     * Fast Placement then checks the new target, every frame.
     */
    @Inject(method = "pick", at = @At("TAIL"))
    private void placementplus$afterPick(float partialTick, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        Bridging.afterPick(minecraft, partialTick);
        FastPlacement.afterPick(minecraft, this::startUseItem);
    }

    /** Never attack or mine the block shown by Bridging. */
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void placementplus$startAttack(CallbackInfoReturnable<Boolean> cir) {
        Bridging.restore((Minecraft) (Object) this);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"))
    private void placementplus$continueAttack(boolean down, CallbackInfo ci) {
        // Called every tick; only when attacking, so the Bridging target stays shown otherwise.
        if (down) {
            Bridging.restore((Minecraft) (Object) this);
        }
    }

    /** Flexible Placement, Fake Sneak and Bridging rewrite the click. */
    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult placementplus$useItemOn(MultiPlayerGameMode gameMode, LocalPlayer player, InteractionHand hand,
                                                      BlockHitResult hit, Operation<InteractionResult> original) {
        return Placement.useItemOn(getConnection(), player, hand, hit, adjusted -> original.call(gameMode, player, hand, adjusted));
    }

    /** Fast Placement replaces vanilla's every-4-ticks repeat once the first block of a press is placed. */
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void placementplus$holdBackRepeat(CallbackInfo ci) {
        if (FastPlacement.suppressVanillaRepeat((Minecraft) (Object) this)) {
            rightClickDelay = Math.max(rightClickDelay, 1);
        }
    }

    /** Continuous Placement: after vanilla has handled the use key, keep placing where the crosshair points. */
    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void placementplus$fastPlacement(CallbackInfo ci) {
        FastPlacement.tick((Minecraft) (Object) this, this::startUseItem, () -> pick(1.0F));
    }

    /** Held-back rotated clicks, and turning the player back on the server; also while a screen is open. */
    @Inject(method = "tick", at = @At("RETURN"))
    private void placementplus$endTick(CallbackInfo ci) {
        ServerLook.endTick((Minecraft) (Object) this, this::startUseItem);
    }
}
