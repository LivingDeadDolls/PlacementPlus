package placementplus.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import placementplus.Preview;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    /**
     * Placement Preview: moves the block outline to where the block will go, after vanilla has worked it out.
     * On 26.1 the outline is extracted in LevelRenderer (26.2 moved it to LevelExtractor).
     */
    @Inject(method = "extractBlockOutline", at = @At("RETURN"))
    private void placementplus$preview(Camera camera, LevelRenderState state, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos pos = Preview.outlinePos(minecraft);
        if (pos != null) {
            state.blockOutlineRenderState = new BlockOutlineRenderState(pos, false,
                    minecraft.options.highContrastBlockOutline().get(), Shapes.block());
        }
    }
}
