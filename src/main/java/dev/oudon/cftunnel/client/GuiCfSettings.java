package dev.oudon.cftunnel.client;

import dev.oudon.cftunnel.CfTunnelMod;
import dev.oudon.cftunnel.Config;
import dev.oudon.cftunnel.Lang;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.File;

/**
 * Global cloudflared settings (path, version, timeout).
 */
public class GuiCfSettings extends GuiScreen {

    private final GuiScreen parent;

    private GuiTextField pathField;
    private GuiTextField versionField;
    private GuiTextField timeoutField;
    private GuiTextField extraArgsField;

    private String error = "";

    public GuiCfSettings(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);

        int left = width / 2 - 150;
        pathField = new GuiTextField(fontRendererObj, left, 64, 300, 18);
        versionField = new GuiTextField(fontRendererObj, left, 110, 120, 18);
        timeoutField = new GuiTextField(fontRendererObj, left, 156, 60, 18);
        extraArgsField = new GuiTextField(fontRendererObj, left, 202, 300, 18);

        pathField.setMaxStringLength(512);
        versionField.setMaxStringLength(32);
        timeoutField.setMaxStringLength(3);
        extraArgsField.setMaxStringLength(256);

        pathField.setText(Config.cloudflaredPath());
        versionField.setText(Config.cloudflaredVersion());
        timeoutField.setText(String.valueOf(Config.readyTimeoutSeconds()));
        extraArgsField.setText(Config.extraArgs());
        pathField.setFocused(true);

        buttonList.add(new GuiButton(1, width / 2 - 105, height - 40, 100, 20, Lang.t("button.save")));
        buttonList.add(new GuiButton(2, width / 2 + 5, height - 40, 100, 20, Lang.t("button.cancel")));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        pathField.updateCursorCounter();
        versionField.updateCursorCounter();
        timeoutField.updateCursorCounter();
        extraArgsField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, Lang.t("settings.title"), width / 2, 20, 0xFFFFFF);

        int left = width / 2 - 150;
        drawString(fontRendererObj, Lang.t("settings.path"), left, 52, 0xA0A0A0);
        drawString(fontRendererObj, Lang.t("settings.pathHint"), left, 84, 0x777777);
        drawString(fontRendererObj, Lang.t("settings.version"), left, 98, 0xA0A0A0);
        drawString(fontRendererObj, Lang.t("settings.versionHint"), left + 126, 110, 0x777777);
        drawString(fontRendererObj, Lang.t("settings.timeout"), left, 144, 0xA0A0A0);
        drawString(fontRendererObj, Lang.t("settings.timeoutHint"), left + 66, 156, 0x777777);
        drawString(fontRendererObj, Lang.t("settings.extraArgs"), left, 190, 0xA0A0A0);
        drawString(fontRendererObj, Lang.t("settings.extraArgsHint"), left, 222, 0x777777);

        File logDir = new File(CfTunnelMod.logDir(), "cftunnel");
        drawString(fontRendererObj, Lang.f("settings.logs", logDir.getAbsolutePath()),
                left, height - 60, 0x777777);

        pathField.drawTextBox();
        versionField.drawTextBox();
        timeoutField.drawTextBox();
        extraArgsField.drawTextBox();

        if (!error.isEmpty()) {
            drawCenteredString(fontRendererObj, error, width / 2, height - 56, 0xFF5555);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        pathField.mouseClicked(mouseX, mouseY, mouseButton);
        versionField.mouseClicked(mouseX, mouseY, mouseButton);
        timeoutField.mouseClicked(mouseX, mouseY, mouseButton);
        extraArgsField.mouseClicked(mouseX, mouseY, mouseButton);
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
        boolean handled = pathField.textboxKeyTyped(typedChar, keyCode);
        handled |= versionField.textboxKeyTyped(typedChar, keyCode);
        handled |= timeoutField.textboxKeyTyped(typedChar, keyCode);
        handled |= extraArgsField.textboxKeyTyped(typedChar, keyCode);
        if (handled) {
            error = "";
        }
    }

    private void cycleFocus() {
        GuiTextField[] fields = new GuiTextField[]{pathField, versionField, timeoutField, extraArgsField};
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
        if (button.id == 1) {
            save();
        } else if (button.id == 2) {
            back();
        }
    }

    private void back() {
        mc.displayGuiScreen(parent != null ? parent : new GuiMainMenu());
    }

    private void save() {
        String path = pathField.getText().trim();
        if (!path.isEmpty() && !new File(path).isFile()) {
            error = Lang.f("error.pathMissing", path);
            return;
        }
        String version = versionField.getText().trim();
        if (version.isEmpty()) {
            error = Lang.t("settings.errorVersion");
            return;
        }
        int timeout;
        try {
            timeout = Integer.parseInt(timeoutField.getText().trim());
        } catch (NumberFormatException e) {
            error = Lang.t("settings.errorTimeout");
            return;
        }
        if (timeout < 5 || timeout > 300) {
            error = Lang.t("settings.errorTimeout");
            return;
        }
        Config.update(timeout, version, extraArgsField.getText().trim(), path);
        back();
    }
}
