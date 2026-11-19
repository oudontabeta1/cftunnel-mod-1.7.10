package dev.oudon.cftunnel.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.oudon.cftunnel.PingTunnel;
import dev.oudon.cftunnel.TunnelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;

public class ClientTickHandler {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        TunnelManager.get().tick();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen instanceof GuiMultiplayer) {
            return;
        }
        GuiScreen screen = mc.currentScreen;
        if (screen instanceof GuiConnecting && GuiHandler.isSwapped()) {
            // The join used the warm tunnel; hand it to the normal lifecycle.
            PingTunnel.Session session = PingTunnel.takeSession();
            if (session != null) {
                TunnelManager.get().adopt(session);
            }
        }
        GuiHandler.restoreSwap();
        PingTunnel.stop();
    }
}
