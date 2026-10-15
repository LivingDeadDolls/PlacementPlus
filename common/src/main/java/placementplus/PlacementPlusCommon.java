package placementplus;

import placementplus.config.PlacementPlusConfig;

/** Loader-independent startup, called from each loader's client entrypoint. */
public final class PlacementPlusCommon {
    private PlacementPlusCommon() {
    }

    public static void init() {
        PlacementPlusConfig.get();
        Constants.LOG.info("Placement Plus loaded on {}", placementplus.platform.Services.PLATFORM.getPlatformName());
    }
}
