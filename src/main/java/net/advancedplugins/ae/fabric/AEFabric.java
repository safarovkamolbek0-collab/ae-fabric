package net.advancedplugins.ae.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AEFabric implements ModInitializer {
    public static final String MOD_ID = "ae_fabric";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        AEEngine.register();
        BlockDrops.register();
        BreakExtras.register();
        ExtraEnchants.register();
        AECommands.register();
        LOGGER.info("AdvancedEnchantments (Fabric port) ishga tushdi.");
    }
}
