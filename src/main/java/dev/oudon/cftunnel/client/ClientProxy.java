package dev.oudon.cftunnel.client;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.oudon.cftunnel.CfTunnelMod;
import dev.oudon.cftunnel.CommonProxy;
import dev.oudon.cftunnel.TunnelManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;

import java.io.File;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        CfTunnelMod.setLogDir(new File(mc != null ? mc.mcDataDir : new File("."), "logs"));

        TunnelManager.get().installShutdownHook();
        MinecraftForge.EVENT_BUS.register(new GuiHandler());
        FMLCommonHandler.instance().bus().register(new ClientTickHandler());
        FMLCommonHandler.instance().bus().register(new KeyHandler());
        KeyHandler.register();
    }
}
