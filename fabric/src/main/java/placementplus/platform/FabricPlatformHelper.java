package placementplus.platform;

import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import placementplus.platform.services.IPlatformHelper;

public class FabricPlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
