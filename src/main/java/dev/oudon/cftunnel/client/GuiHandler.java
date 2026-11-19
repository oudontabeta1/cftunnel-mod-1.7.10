package dev.oudon.cftunnel.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.oudon.cftunnel.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraftforge.client.event.GuiScreenEvent;

/**
 * マルチプレイ画面に「トンネル」ボタンを追加する。
 */
public class GuiHandler {

    private static final int BUTTON_ID = 0x0CF7001;

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiMultiplayer)) {
            return;
        }
        for (Object o : event.buttonList) {
            if (o instanceof GuiButton && ((GuiButton) o).id == BUTTON_ID) {
                return;
            }
        }
        int buttonWidth = 96;
        int x = Math.max(4, event.gui.width - buttonWidth - 4);
        event.buttonList.add(new GuiButton(BUTTON_ID, x, 6, buttonWidth, 20,
                Lang.t("button.tunnels")));
    }

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (event.button != null && event.button.id == BUTTON_ID) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                mc.displayGuiScreen(new GuiTunnelList(event.gui));
            }
        }
    }
}
