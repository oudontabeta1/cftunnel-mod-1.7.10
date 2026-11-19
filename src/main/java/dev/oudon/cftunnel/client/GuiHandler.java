package dev.oudon.cftunnel.client;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import dev.oudon.cftunnel.Lang;
import dev.oudon.cftunnel.ServerConfigStore;
import dev.oudon.cftunnel.ServerTunnelConfig;
import dev.oudon.cftunnel.TunnelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ServerListEntryNormal;
import net.minecraft.client.gui.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;

import java.lang.reflect.Field;

/**
 * Integrates Cloudflare tunnel settings into the vanilla multiplayer screen.
 */
public class GuiHandler {

    private static final int BUTTON_SETTINGS = 0x0CF7001;
    private static final int BUTTON_SERVER = 0x0CF7002;

    /** From MCP mappings: serverListSelector / field_146803_h */
    private static final Field SERVER_LIST_SELECTOR = findServerListField();

    private long lastClickAt;
    private int lastClickX;
    private int lastClickY;
    private Object lastClickScreen;
    private GuiButton serverButton;
    private GuiScreen serverButtonOwner;

    private static Field findServerListField() {
        try {
            return ReflectionHelper.findField(GuiMultiplayer.class,
                    "serverListSelector", "field_146803_h");
        } catch (Throwable t) {
            return null;
        }
    }

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiMultiplayer)) {
            return;
        }
        GuiMultiplayer gui = (GuiMultiplayer) event.gui;

        if (!hasButton(event, BUTTON_SETTINGS)) {
            event.buttonList.add(new GuiButton(BUTTON_SETTINGS,
                    Math.max(4, gui.width - 104), 6, 100, 20, Lang.t("button.settings")));
        }
        if (!hasButton(event, BUTTON_SERVER)) {
            serverButton = new GuiButton(BUTTON_SERVER,
                    gui.width / 2 + 160, gui.height - 28, 96, 20, Lang.t("button.serverCf"));
            serverButtonOwner = gui;
            event.buttonList.add(serverButton);
        }
    }

    private boolean hasButton(GuiScreenEvent.InitGuiEvent.Post event, int id) {
        for (Object o : event.buttonList) {
            if (o instanceof GuiButton && ((GuiButton) o).id == id) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.gui instanceof GuiMultiplayer) || event.button == null) {
            return;
        }
        GuiMultiplayer gui = (GuiMultiplayer) event.gui;
        int id = event.button.id;
        if (id == BUTTON_SETTINGS) {
            event.setCanceled(true);
            Minecraft.getMinecraft().displayGuiScreen(new GuiCfSettings(gui));
            return;
        }
        if (id == BUTTON_SERVER) {
            event.setCanceled(true);
            ServerData selected = selectedServer(gui);
            if (selected != null) {
                Minecraft.getMinecraft().displayGuiScreen(new GuiServerTunnelEdit(gui, selected));
            }
            return;
        }
        // Vanilla "Join Server" button.
        if (id == 1) {
            ServerData selected = selectedServer(gui);
            ServerTunnelConfig cfg = ServerConfigStore.get().find(selected);
            if (cfg != null && cfg.enabled) {
                event.setCanceled(true);
                join(gui, selected, cfg);
            }
        }
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiMultiplayer)) {
            return;
        }
        GuiMultiplayer gui = (GuiMultiplayer) event.gui;
        ServerData selected = selectedServer(gui);
        if (serverButtonOwner == gui && serverButton != null) {
            serverButton.enabled = selected != null;
        }
        ServerTunnelConfig cfg = ServerConfigStore.get().find(selected);
        String text;
        int color;
        if (cfg != null && cfg.enabled) {
            String host = cfg.hostnameOr(ServerConfigStore.hostnameFromAddress(
                    selected == null ? "" : selected.serverIP));
            String port = cfg.localPort > 0 ? ":" + cfg.localPort : " (auto)";
            text = Lang.f("server.statusOn", host, port);
            color = 0x55D06B;
        } else {
            text = Lang.t("server.statusOff");
            color = 0x808080;
        }
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(text, 6, 8, color);
    }

    /**
     * Cancels the second click of a double click on a tunnel-enabled server so
     * vanilla does not connect to the public address directly.
     */
    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GuiMultiplayer)) {
            lastClickScreen = null;
            return;
        }
        if (event.button != 0 || !event.buttonstate) {
            return;
        }
        GuiMultiplayer gui = (GuiMultiplayer) mc.currentScreen;
        int mouseX = event.x * gui.width / Math.max(1, mc.displayWidth);
        int mouseY = gui.height - event.y * gui.height / Math.max(1, mc.displayHeight) - 1;
        if (mouseY < 32 || mouseY > gui.height - 64) {
            lastClickScreen = null;
            return;
        }

        long now = System.currentTimeMillis();
        boolean doubleClick = lastClickScreen == gui
                && now - lastClickAt <= 350L
                && Math.abs(mouseY - lastClickY) <= 12
                && Math.abs(mouseX - lastClickX) <= 60;
        lastClickAt = now;
        lastClickX = mouseX;
        lastClickY = mouseY;
        lastClickScreen = gui;

        if (!doubleClick) {
            return;
        }
        lastClickScreen = null;
        ServerData selected = selectedServer(gui);
        ServerTunnelConfig cfg = ServerConfigStore.get().find(selected);
        if (cfg != null && cfg.enabled) {
            event.setCanceled(true);
            join(gui, selected, cfg);
        }
    }

    private static void join(GuiMultiplayer gui, ServerData server, ServerTunnelConfig cfg) {
        String host = cfg.hostnameOr(ServerConfigStore.hostnameFromAddress(server.serverIP));
        if (!ServerConfigStore.isValidHostname(host)) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiServerTunnelEdit(gui, server));
            return;
        }
        TunnelManager.get().connect(gui, server.serverName, host, cfg.localPort);
        Minecraft.getMinecraft().displayGuiScreen(new GuiTunnelProgress(gui, server.serverName));
    }

    private static ServerData selectedServer(GuiMultiplayer gui) {
        if (SERVER_LIST_SELECTOR == null) {
            return null;
        }
        try {
            Object list = SERVER_LIST_SELECTOR.get(gui);
            if (!(list instanceof ServerSelectionList)) {
                return null;
            }
            ServerSelectionList selection = (ServerSelectionList) list;
            int index = selection.func_148193_k();
            if (index < 0) {
                return null;
            }
            GuiListExtended.IGuiListEntry entry = selection.getListEntry(index);
            if (entry instanceof ServerListEntryNormal) {
                return ((ServerListEntryNormal) entry).getServerData();
            }
        } catch (Throwable t) {
            return null;
        }
        return null;
    }
}
