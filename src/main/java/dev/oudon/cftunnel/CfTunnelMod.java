package dev.oudon.cftunnel;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = CfTunnelMod.MODID, name = CfTunnelMod.NAME, version = CfTunnelMod.VERSION,
        acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.7.10]")
public class CfTunnelMod {

    public static final String MODID = "cftunnel";
    public static final String NAME = "Cloudflared Tunnel Connect";
    public static final String VERSION = "1.1.0";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "dev.oudon.cftunnel.client.ClientProxy",
            serverSide = "dev.oudon.cftunnel.CommonProxy")
    public static CommonProxy proxy;

    private static File configDir;
    private static File logDir;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configDir = new File(event.getModConfigurationDirectory(), "cftunnel");
        if (logDir == null) {
            logDir = new File(configDir.getParentFile(), "../logs");
        }
        Config.load(configDir);
        ServerConfigStore.get().load(configDir);
        proxy.preInit(event);
    }

    public static File configDir() {
        return configDir;
    }

    public static File logDir() {
        return logDir;
    }

    public static void setLogDir(File dir) {
        logDir = dir;
    }
}
