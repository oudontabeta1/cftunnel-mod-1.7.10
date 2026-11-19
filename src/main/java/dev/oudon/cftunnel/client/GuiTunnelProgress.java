package dev.oudon.cftunnel.client;

import dev.oudon.cftunnel.Lang;
import dev.oudon.cftunnel.TunnelManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/**
 * Progress screen shown while the tunnel is being established.
 */
public class GuiTunnelProgress extends GuiScreen {

    private final GuiScreen parent;
    private final String displayName;

    public GuiTunnelProgress(GuiScreen parent, String displayName) {
        this.parent = parent;
        this.displayName = displayName == null ? "" : displayName;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(1, width / 2 - 60, height / 2 + 46, 120, 20,
                Lang.t("button.cancel")));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        TunnelManager manager = TunnelManager.get();

        drawCenteredString(fontRendererObj, Lang.t("progress.title"), width / 2,
                height / 2 - 70, 0xFFFFFF);
        drawCenteredString(fontRendererObj, trim(displayName, 320), width / 2,
                height / 2 - 52, 0xA0A0A0);
        drawCenteredString(fontRendererObj, trim(manager.statusText(), 400), width / 2,
                height / 2 - 20, 0xFFFFFF);

        double progress = manager.progress();
        int barLeft = width / 2 - 100;
        int barRight = width / 2 + 100;
        int barTop = height / 2 + 4;
        Gui.drawRect(barLeft - 1, barTop - 1, barRight + 1, barTop + 9, 0xFF000000);
        Gui.drawRect(barLeft, barTop, barRight, barTop + 8, 0x80404040);
        if (progress >= 0) {
            int filled = (int) Math.round((barRight - barLeft) * Math.min(1.0, progress));
            Gui.drawRect(barLeft, barTop, barLeft + filled, barTop + 8, 0xFF4A90D9);
        } else {
            int dots = (int) (System.currentTimeMillis() / 400L % 4L);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dots; i++) {
                sb.append('.');
            }
            fontRendererObj.drawStringWithShadow(sb.toString(), width / 2 + 105, barTop - 4,
                    0xFFFFFF);
        }

        drawCenteredString(fontRendererObj, Lang.t("progress.hint"), width / 2,
                height / 2 + 24, 0x808080);

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
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            cancel();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) {
            cancel();
        }
    }

    private void cancel() {
        TunnelManager.get().cancel();
        mc.displayGuiScreen(parent);
    }
}
