package placementplus;

import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import placementplus.config.PlacementPlusConfig;

/** The mod's key mappings. They are registered by the loader's client entrypoint and handed over with {@link #set}. */
public final class Keys {
    public static final String ROTATION = "key.placementplus.rotation";
    public static final String ADJACENT = "key.placementplus.adjacent";
    public static final String TOGGLE_FAST_PLACEMENT = "key.placementplus.toggle_fast_placement";
    public static final String CYCLE_RESTRICTION = "key.placementplus.cycle_restriction";
    public static final String TOGGLE_BRIDGING = "key.placementplus.toggle_bridging";
    public static final String TOGGLE_SCAFFOLD = "key.placementplus.toggle_scaffold";

    private static KeyMapping rotation;
    private static KeyMapping adjacent;
    private static KeyMapping toggleFastPlacement;
    private static KeyMapping cycleRestriction;
    private static KeyMapping toggleBridging;
    private static KeyMapping toggleScaffold;

    private Keys() {
    }

    public static void set(KeyMapping rotation, KeyMapping adjacent, KeyMapping toggleFastPlacement, KeyMapping cycleRestriction,
                           KeyMapping toggleBridging, KeyMapping toggleScaffold) {
        Keys.rotation = rotation;
        Keys.adjacent = adjacent;
        Keys.toggleFastPlacement = toggleFastPlacement;
        Keys.cycleRestriction = cycleRestriction;
        Keys.toggleBridging = toggleBridging;
        Keys.toggleScaffold = toggleScaffold;
    }

    public static boolean rotationDown() {
        return rotation != null && rotation.isDown();
    }

    public static boolean adjacentDown() {
        return adjacent != null && adjacent.isDown();
    }

    /** Handles the toggle keys. Called every client tick. */
    public static void tick(Minecraft minecraft) {
        if (toggleFastPlacement == null) {
            return;
        }
        PlacementPlusConfig config = PlacementPlusConfig.get();
        // Presses are always drained, so none are replayed later; for toggles only the parity matters.
        boolean flipFast = drain(toggleFastPlacement) % 2 == 1;
        int cycles = drain(cycleRestriction);
        boolean flipBridging = drain(toggleBridging) % 2 == 1;
        boolean flipScaffold = drain(toggleScaffold) % 2 == 1;
        if (minecraft.player == null) {
            return;
        }
        if (flipFast) {
            config.fastPlacement = !config.fastPlacement;
            announceToggle(minecraft, "placementplus.config.fast_placement", config.fastPlacement);
        }
        if (cycles > 0) {
            PlacementRestriction[] values = PlacementRestriction.values();
            config.placementRestriction = values[(config.placementRestriction.ordinal() + cycles) % values.length];
            announce(minecraft, "placementplus.config.placement_restriction", Component.translatable(
                    "placementplus.config.placement_restriction." + config.placementRestriction.name().toLowerCase(Locale.ROOT)));
        }
        if (flipBridging) {
            config.bridging = !config.bridging;
            announceToggle(minecraft, "placementplus.config.bridging", config.bridging);
        }
        if (flipScaffold) {
            config.scaffoldPlacement = !config.scaffoldPlacement;
            announceToggle(minecraft, "placementplus.config.scaffold_placement", config.scaffoldPlacement);
        }
        if (flipFast || cycles > 0 || flipBridging || flipScaffold) {
            PlacementPlusConfig.save();
        }
    }

    private static int drain(KeyMapping key) {
        int count = 0;
        while (key.consumeClick()) {
            count++;
        }
        return count;
    }

    private static void announceToggle(Minecraft minecraft, String nameKey, boolean on) {
        announce(minecraft, nameKey, Component.translatable(on ? "options.on" : "options.off"));
    }

    private static void announce(Minecraft minecraft, String nameKey, Component value) {
        minecraft.player.sendOverlayMessage(Component.translatable("placementplus.message.toggled", Component.translatable(nameKey), value));
    }
}
