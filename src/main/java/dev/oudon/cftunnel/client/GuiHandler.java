package dev.oudon.cftunnel.client;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import dev.oudon.cftunnel.CfTunnelMod;
import dev.oudon.cftunnel.Lang;
import dev.oudon.cftunnel.PingTunnel;
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

    private static ServerData swappedServer;
    private static String swappedOriginal;
    private static String swappedKey;
    private static int swappedPort = -1;
    private static String pingedToken;

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
        restoreSwap();
        pingedToken = null;
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
            restoreSwap();
            ServerData selected = selectedServer(gui);
            if (selected != null) {
                Minecraft.getMinecraft().displayGuiScreen(new GuiServerTunnelEdit(gui, selected));
            }
            return;
        }
        // Delete / Add / Edit / Refresh may rewrite servers.dat.
        if (id == 2 || id == 3 || id == 7 || id == 8) {
            restoreSwap();
            return;
        }
        if (id == 1) {
            ServerData selected = selectedServer(gui);
            ServerTunnelConfig cfg = cfgFor(selected);
            if (cfg != null && cfg.enabled) {
                if (isSwappedFor(selected)) {
                    // The server address already points at the local tunnel.
                    return;
                }
                event.setCanceled(true);
                join(gui, selected, cfg);
            }
        }
    }

    @SubscribeEvent
    public void onDrawScreenPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (event.gui instanceof GuiMultiplayer) {
            // Do not let drawing (server icon saving) persist a 127.0.0.1 address.
            restoreSwap();
        }
    }

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiMultiplayer)) {
            return;
        }
        GuiMultiplayer gui = (GuiMultiplayer) event.gui;
        ServerData selected = selectedServer(gui);
        if (serverButtonOwner == gui && serverButton != null) {
            serverButton.enabled = selected != null;
        }

        ServerTunnelConfig cfg = cfgFor(selected);
        String key = selected == null ? null : originalIp(selected);

        if (cfg != null && cfg.enabled && key != null) {
            String host = cfg.hostnameOr(ServerConfigStore.hostnameFromAddress(key));
            if (ServerConfigStore.isValidHostname(host)) {
                // Keep a tunnel warm and point the server entry at it. This also
                // covers joins started with the Enter key or by double click.
                PingTunnel.ensure(key, host, cfg.localPort, cfg.tokenPathOrEmpty());
                int port = PingTunnel.portFor(key);
                if (port > 0) {
                    applySwap(selected, key, port);
                    String token = key + ":" + port;
                    if (!token.equals(pingedToken)) {
                        pingedToken = token;
                        ping(gui, selected);
                    }
                } else {
                    restoreSwap();
                }
            } else {
                PingTunnel.stop();
                restoreSwap();
            }
        } else {
            PingTunnel.stop();
            restoreSwap();
        }

        String text;
        int color;
        if (cfg != null && cfg.enabled) {
            String host = cfg.hostnameOr(ServerConfigStore.hostnameFromAddress(
                    key == null ? "" : key));
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
     * Cancels the second click of a double click when the tunnel for that
     * server is not ready yet; otherwise vanilla connects to the local port.
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
        ServerTunnelConfig cfg = cfgFor(selected);
        if (cfg != null && cfg.enabled && !isSwappedFor(selected)) {
            event.setCanceled(true);
            join(gui, selected, cfg);
        }
    }

    private static void join(GuiMultiplayer gui, ServerData server, ServerTunnelConfig cfg) {
        String host = cfg.hostnameOr(ServerConfigStore.hostnameFromAddress(originalIp(server)));
        restoreSwap();
        if (!ServerConfigStore.isValidHostname(host)) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiServerTunnelEdit(gui, server));
            return;
        }
        PingTunnel.stop();
        TunnelManager.get().connect(gui, server.serverName, host, cfg.localPort, cfg.tokenPathOrEmpty());
        Minecraft.getMinecraft().displayGuiScreen(new GuiTunnelProgress(gui, server.serverName));
    }

    private static void ping(GuiMultiplayer gui, ServerData server) {
        try {
            gui.getOldServerPinger().ping(server);
        } catch (Exception e) {
            CfTunnelMod.LOG.debug("Ping through tunnel failed: {}", e.toString());
        }
    }

    private static void applySwap(ServerData server, String key, int port) {
        if (server == swappedServer && swappedPort == port) {
            return;
        }
        restoreSwap();
        swappedServer = server;
        swappedOriginal = server.serverIP;
        swappedKey = key;
        swappedPort = port;
        server.serverIP = "127.0.0.1:" + port;
    }

    public static void restoreSwap() {
        if (swappedServer != null && swappedOriginal != null) {
            swappedServer.serverIP = swappedOriginal;
        }
        swappedServer = null;
        swappedOriginal = null;
        swappedKey = null;
        swappedPort = -1;
    }

    public static boolean isSwapped() {
        return swappedServer != null;
    }

    private static boolean isSwappedFor(ServerData server) {
        return server != null && server == swappedServer && swappedPort > 0;
    }

    private static String originalIp(ServerData server) {
        if (server == null) {
            return null;
        }
        if (server == swappedServer && swappedOriginal != null) {
            return swappedOriginal;
        }
        return server.serverIP;
    }

    private static ServerTunnelConfig cfgFor(ServerData server) {
        String key = originalIp(server);
        return key == null ? null : ServerConfigStore.get().findByKey(key);
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
