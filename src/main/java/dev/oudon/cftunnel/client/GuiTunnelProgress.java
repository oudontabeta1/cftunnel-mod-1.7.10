package dev.oudon.cftunnel.client;

import dev.oudon.cftunnel.Lang;
import dev.oudon.cftunnel.TunnelManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Progress screen shown while the tunnel is being established.
 */
public class GuiTunnelProgress extends GuiScreen {

    private final GuiScreen parent;
    private final String displayName;

    private GuiButton copyButton;
    private GuiButton openButton;
    private String copiedNotice = "";

    public GuiTunnelProgress(GuiScreen parent, String displayName) {
        this.parent = parent;
        this.displayName = displayName == null ? "" : displayName;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int center = width / 2;
        int y = height / 2 + 72;
        copyButton = new GuiButton(2, center - 150, y, 90, 20, Lang.t("button.copyUrl"));
        openButton = new GuiButton(3, center - 50, y, 110, 20, Lang.t("button.openBrowser"));
        buttonList.add(copyButton);
        buttonList.add(openButton);
        buttonList.add(new GuiButton(1, center + 70, y, 90, 20, Lang.t("button.cancel")));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        String url = TunnelManager.get().loginUrl();
        boolean hasUrl = url != null && !url.isEmpty();
        if (copyButton != null) {
            copyButton.enabled = hasUrl;
        }
        if (openButton != null) {
            openButton.enabled = hasUrl;
        }
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

        String url = manager.loginUrl();
        if (url != null && !url.isEmpty()) {
            drawCenteredString(fontRendererObj, Lang.t("progress.openHint"), width / 2,
                    height / 2 + 18, 0xE0C060);
            int y = height / 2 + 30;
            for (String line : wrap(url, 420, 3)) {
                drawCenteredString(fontRendererObj, line, width / 2, y, 0x9E9E9E);
                y += 10;
            }
        } else {
            drawCenteredString(fontRendererObj, Lang.t("progress.hint"), width / 2,
                    height / 2 + 24, 0x808080);
        }

        if (!copiedNotice.isEmpty()) {
            drawCenteredString(fontRendererObj, copiedNotice, width / 2, height / 2 + 58,
                    0x55D06B);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private List<String> wrap(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<String>();
        String rest = text;
        while (!rest.isEmpty() && lines.size() < maxLines) {
            int index = 0;
            while (index < rest.length()
                    && fontRendererObj.getStringWidth(rest.substring(0, index + 1)) <= maxWidth) {
                index++;
            }
            if (index <= 0) {
                index = Math.min(1, rest.length());
            }
            if (index < rest.length() && lines.size() == maxLines - 1) {
                lines.add(rest.substring(0, Math.max(0, index - 3)) + "...");
                return lines;
            }
            lines.add(rest.substring(0, index));
            rest = rest.substring(index);
        }
        return lines;
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
        String url = TunnelManager.get().loginUrl();
        if (button.id == 1) {
            cancel();
        } else if (button.id == 2 && url != null && !url.isEmpty()) {
            setClipboardString(url);
            copiedNotice = Lang.t("progress.copied");
        } else if (button.id == 3 && url != null && !url.isEmpty()) {
            openBrowser(url);
        }
    }

    private void openBrowser(String url) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else {
                pb = new ProcessBuilder("xdg-open", url);
            }
            pb.start();
        } catch (Exception ignored) {
            // The user can still copy the URL manually.
        }
    }

    private void cancel() {
        TunnelManager.get().cancel();
        mc.displayGuiScreen(parent);
    }
}
