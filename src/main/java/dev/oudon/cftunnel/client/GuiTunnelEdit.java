package dev.oudon.cftunnel.client;

import dev.oudon.cftunnel.Lang;
import dev.oudon.cftunnel.ProfileStore;
import dev.oudon.cftunnel.TunnelManager;
import dev.oudon.cftunnel.TunnelProfile;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.util.regex.Pattern;

/**
 * トンネル設定の編集画面。hostname だけでなく cloudflared のパスも UI で指定できる。
 */
public class GuiTunnelEdit extends GuiScreen {

    private static final Pattern HOSTNAME = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$");

    private final GuiScreen parent;
    private final int index;

    private GuiTextField nameField;
    private GuiTextField hostField;
    private GuiTextField portField;
    private GuiTextField pathField;

    private GuiButton saveButton;
    private GuiButton loginButton;

    private String error = "";
    private String notice = "";

    public GuiTunnelEdit(GuiScreen parent, int index) {
        this.parent = parent;
        this.index = index;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);

        int left = width / 2 - 150;
        int fieldWidth = 300;

        nameField = new GuiTextField(fontRendererObj, left, 58, fieldWidth, 18);
        hostField = new GuiTextField(fontRendererObj, left, 96, fieldWidth, 18);
        portField = new GuiTextField(fontRendererObj, left, 134, 80, 18);
        pathField = new GuiTextField(fontRendererObj, left, 172, fieldWidth, 18);
        nameField.setMaxStringLength(64);
        hostField.setMaxStringLength(253);
        portField.setMaxStringLength(5);
        pathField.setMaxStringLength(512);

        TunnelProfile profile = ProfileStore.get().get(index);
        if (profile != null) {
            nameField.setText(profile.name);
            hostField.setText(profile.hostname);
            portField.setText(profile.localPort > 0 ? String.valueOf(profile.localPort) : "");
            pathField.setText(profile.cloudflaredPath);
        }

        hostField.setFocused(true);

        saveButton = new GuiButton(1, width / 2 - 155, height - 40, 100, 20, Lang.t("button.save"));
        loginButton = new GuiButton(3, width / 2 - 50, height - 40, 100, 20, Lang.t("button.login"));
        GuiButton cancel = new GuiButton(2, width / 2 + 55, height - 40, 100, 20,
                Lang.t("button.cancel"));
        buttonList.add(saveButton);
        buttonList.add(loginButton);
        buttonList.add(cancel);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        nameField.updateCursorCounter();
        hostField.updateCursorCounter();
        portField.updateCursorCounter();
        pathField.updateCursorCounter();
        if (TunnelManager.get().isLoginRunning()) {
            notice = TunnelManager.get().loginMessage();
            loginButton.enabled = false;
        } else {
            loginButton.enabled = true;
            if (notice.equals(Lang.t("status.loginBrowser"))) {
                notice = TunnelManager.get().loginMessage();
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj,
                index >= 0 ? Lang.t("edit.title") : Lang.t("edit.titleNew"),
                width / 2, 20, 0xFFFFFF);

        int left = width / 2 - 150;
        drawString(fontRendererObj, Lang.t("edit.name"), left, 46, 0xA0A0A0);
        drawString(fontRendererObj, Lang.t("edit.hostname"), left, 84, 0xA0A0A0);
        drawString(fontRendererObj, Lang.t("edit.port"), left, 122, 0xA0A0A0);
        drawString(fontRendererObj, Lang.t("edit.path"), left, 160, 0xA0A0A0);

        drawString(fontRendererObj, Lang.t("edit.hostnameHint"), left, 200, 0x777777);
        drawString(fontRendererObj, Lang.t("edit.portHint"), left + 88, 134, 0x777777);
        drawString(fontRendererObj, Lang.t("edit.pathHint"), left, 210, 0x777777);

        nameField.drawTextBox();
        hostField.drawTextBox();
        portField.drawTextBox();
        pathField.drawTextBox();

        if (!error.isEmpty()) {
            drawCenteredString(fontRendererObj, error, width / 2, height - 58, 0xFF5555);
        } else if (!notice.isEmpty()) {
            drawCenteredString(fontRendererObj, trim(notice, 400), width / 2, height - 58, 0xE0C060);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
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
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
        hostField.mouseClicked(mouseX, mouseY, mouseButton);
        portField.mouseClicked(mouseX, mouseY, mouseButton);
        pathField.mouseClicked(mouseX, mouseY, mouseButton);
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
            cycleFocus();
            return;
        }
        boolean handled = nameField.textboxKeyTyped(typedChar, keyCode);
        handled |= hostField.textboxKeyTyped(typedChar, keyCode);
        handled |= portField.textboxKeyTyped(typedChar, keyCode);
        handled |= pathField.textboxKeyTyped(typedChar, keyCode);
        if (handled) {
            error = "";
            notice = "";
        }
    }

    private void cycleFocus() {
        GuiTextField[] fields = new GuiTextField[]{nameField, hostField, portField, pathField};
        int current = -1;
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].isFocused()) {
                current = i;
                break;
            }
        }
        int next = (current + 1) % fields.length;
        for (int i = 0; i < fields.length; i++) {
            fields[i].setFocused(i == next);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 1:
                save();
                break;
            case 2:
                back();
                break;
            case 3:
                login();
                break;
            default:
                break;
        }
    }

    private void back() {
        mc.displayGuiScreen(parent != null ? parent : new GuiMainMenu());
    }

    private void login() {
        TunnelProfile probe = buildProfile();
        if (probe == null) {
            return;
        }
        notice = Lang.t("status.loginBrowser");
        TunnelManager.get().loginAsync(probe, null);
    }

    private TunnelProfile buildProfile() {
        String host = normalizeHostname(hostField.getText());
        if (!isValidHost(host)) {
            error = Lang.t("error.invalidHostname");
            return null;
        }
        int port = 0;
        String portText = portField.getText().trim();
        if (!portText.isEmpty()) {
            try {
                port = Integer.parseInt(portText);
            } catch (NumberFormatException e) {
                error = Lang.t("error.invalidPort");
                return null;
            }
            if (port < 1 || port > 65535) {
                error = Lang.t("error.invalidPort");
                return null;
            }
        }
        String path = pathField.getText().trim();
        if (!path.isEmpty() && !new File(path).isFile()) {
            error = Lang.f("error.pathMissing", path);
            return null;
        }
        TunnelProfile profile = index >= 0 ? ProfileStore.get().get(index) : null;
        profile = profile != null ? profile.copy() : new TunnelProfile();
        String name = nameField.getText().trim();
        profile.name = name.isEmpty() ? host : name;
        profile.hostname = host;
        profile.localPort = port;
        profile.cloudflaredPath = path;
        return profile;
    }

    private void save() {
        TunnelProfile profile = buildProfile();
        if (profile == null) {
            return;
        }
        if (index >= 0) {
            ProfileStore.get().replace(index, profile);
        } else {
            ProfileStore.get().add(profile);
        }
        mc.displayGuiScreen(new GuiTunnelList(parent));
    }

    private static String normalizeHostname(String raw) {
        String value = raw == null ? "" : raw.trim();
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("https://")) {
            value = value.substring(8);
        } else if (lower.startsWith("http://")) {
            value = value.substring(7);
        } else if (lower.startsWith("tcp://")) {
            value = value.substring(6);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        return value.trim();
    }

    private static boolean isValidHost(String host) {
        return host != null && !host.isEmpty() && !host.contains("..") && HOSTNAME.matcher(host).matches();
    }
}
