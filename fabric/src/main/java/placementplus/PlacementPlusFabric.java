package placementplus;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class PlacementPlusFabric implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "main"));

    @Override
    public void onInitializeClient() {
        PlacementPlusCommon.init();

        // All unbound by default; set them in Controls.
        Keys.set(register(Keys.ROTATION), register(Keys.ADJACENT), register(Keys.TOGGLE_FAST_PLACEMENT),
                register(Keys.CYCLE_RESTRICTION), register(Keys.TOGGLE_BRIDGING), register(Keys.TOGGLE_SCAFFOLD));
        ClientTickEvents.END_CLIENT_TICK.register(Keys::tick);
    }

    private static KeyMapping register(String name) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.UNKNOWN.getValue(), CATEGORY));
    }
}
