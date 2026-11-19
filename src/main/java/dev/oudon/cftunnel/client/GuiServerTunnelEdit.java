package dev.oudon.cftunnel.client;

import dev.oudon.cftunnel.Lang;
import dev.oudon.cftunnel.ServerConfigStore;
import dev.oudon.cftunnel.ServerTunnelConfig;
import dev.oudon.cftunnel.TunnelManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.ServerData;
import org.lwjgl.input.Keyboard;

/**
 * Per-server tunnel settings, opened from the multiplayer server list.
 */
public class GuiServerTunnelEdit extends GuiScreen {

    private final GuiScreen parent;
    private final ServerData server;

    private GuiTextField hostnameField;
    private GuiTextField portField;
    private GuiButton toggleButton;

    private boolean enabled;
    private String error = "";
    private String notice = "";

    public GuiServerTunnelEdit(GuiScreen parent, ServerData server) {
        this.parent = parent;
        this.server = server;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        if (server == null) {
            mc.displayGuiScreen(parent);
            return;
        }
        Keyboard.enableRepeatEvents(true);

        ServerTunnelConfig existing = ServerConfigStore.get().find(server);
        String hostname = "";
        int port = 0;
        if (existing != null) {
            enabled = existing.enabled;
            hostname = existing.hostname;
            port = existing.localPort;
        } else {
            hostname = ServerConfigStore.hostnameFromAddress(server.serverIP);
        }

        int left = width / 2 - 150;
        hostnameField = new GuiTextField(fontRendererObj, left, 96, 300, 18);
        portField = new GuiTextField(fontRendererObj, left, 140, 80, 18);
        hostnameField.setMaxStringLength(253);
        portField.setMaxStringLength(5);
        hostnameField.setText(hostname);
        portField.setText(port > 0 ? String.valueOf(port) : "");
        hostnameField.setFocused(true);

        toggleButton = new GuiButton(3, width / 2 - 150, 56, 160, 20, toggleLabel());
        buttonList.add(toggleButton);
        buttonList.add(new GuiButton(4, width / 2 + 4, height - 40, 90, 20, Lang.t("button.login")));
        buttonList.add(new GuiButton(1, width / 2 - 150, height - 40, 90, 20, Lang.t("button.save")));
        buttonList.add(new GuiButton(2, width / 2 - 50, height - 40, 90, 20, Lang.t("button.cancel")));
    }

    private String toggleLabel() {
        return Lang.t(enabled ? "server.toggleOn" : "server.toggleOff");
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        hostnameField.updateCursorCounter();
        portField.updateCursorCounter();
        toggleButton.displayString = toggleLabel();
        if (TunnelManager.get().isLoginRunning()) {
            notice = TunnelManager.get().loginMessage();
        } else if (notice.equals(Lang.t("status.loginBrowser"))
                && !TunnelManager.get().loginMessage().isEmpty()) {
            notice = TunnelManager.get().loginMessage();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, Lang.t("server.title"), width / 2, 20, 0xFFFFFF);

        int left = width / 2 - 150;
        drawString(fontRendererObj, trim(serverName(), 300), left, 38, 0x9E9E9E);
        drawString(fontRendererObj, Lang.t("server.hostname"), left, 84, 0xA0A0A0);
        drawString(fontRendererObj, Lang.t("server.hostnameHint"), left, 118, 0x777777);
        drawString(fontRendererObj, Lang.t("server.port"), left, 128, 0xA0A0A0);
        drawString(fontRendererObj, Lang.t("server.portHint"), left + 88, 140, 0x777777);

        hostnameField.drawTextBox();
        portField.drawTextBox();

        if (!error.isEmpty()) {
            drawCenteredString(fontRendererObj, error, width / 2, height - 58, 0xFF5555);
        } else if (!notice.isEmpty()) {
            drawCenteredString(fontRendererObj, trim(notice, 400), width / 2, height - 58, 0xE0C060);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String serverName() {
        return server == null ? "" : server.serverName + "  (" + server.serverIP + ")";
    }

    private String trim(String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (fontRendererObj.getStringWidth(text) <= maxWidth) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (fontRendererObj.getStringWidth(sb.toString() + c + "...") > maxWidth) {
                break;
            }
            sb.append(c);
        }
        return sb + "...";
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        hostnameField.mouseClicked(mouseX, mouseY, mouseButton);
        portField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            back();
            return;
        }
        if (keyCode == 28) {
            save();
            return;
        }
        if (keyCode == 15) {
            boolean host = hostnameField.isFocused();
            hostnameField.setFocused(!host);
            portField.setFocused(host);
            return;
        }
        boolean handled = hostnameField.textboxKeyTyped(typedChar, keyCode);
        handled |= portField.textboxKeyTyped(typedChar, keyCode);
        if (handled) {
            error = "";
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) {
            save();
        } else if (button.id == 2) {
            back();
        } else if (button.id == 3) {
            enabled = !enabled;
            if (enabled && hostnameField.getText().trim().isEmpty()) {
                hostnameField.setText(ServerConfigStore.hostnameFromAddress(server.serverIP));
            }
        } else if (button.id == 4) {
            login();
        }
    }

    private void login() {
        String host = hostnameField.getText().trim();
        if (!ServerConfigStore.isValidHostname(host)) {
            error = Lang.t("error.invalidHostname");
            return;
        }
        notice = Lang.t("status.loginBrowser");
        TunnelManager.get().loginAsync(host, null);
    }

    private void back() {
        mc.displayGuiScreen(parent);
    }

    private void save() {
        String host = hostnameField.getText().trim();
        if (enabled && !ServerConfigStore.isValidHostname(host)) {
            error = Lang.t("error.invalidHostname");
            return;
        }
        int port = 0;
        String portText = portField.getText().trim();
        if (!portText.isEmpty()) {
            try {
                port = Integer.parseInt(portText);
            } catch (NumberFormatException e) {
                error = Lang.t("error.invalidPort");
                return;
            }
            if (port < 1 || port > 65535) {
                error = Lang.t("error.invalidPort");
                return;
            }
        }

        ServerTunnelConfig cfg = ServerConfigStore.get().findOrCreate(server);
        cfg.enabled = enabled;
        cfg.hostname = host;
        cfg.localPort = port;
        ServerConfigStore.get().store(cfg);
        back();
    }
}
