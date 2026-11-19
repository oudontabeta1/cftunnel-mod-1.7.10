package dev.oudon.cftunnel.client;

import dev.oudon.cftunnel.Lang;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * 接続失敗時の詳細表示。ログ末尾をコピーできる。
 */
public class GuiTunnelError extends GuiScreen {

    private final GuiScreen parent;
    private final String message;
    private final String logTail;
    private final String logPath;
    private String status = "";

    public GuiTunnelError(GuiScreen parent, String message, String logTail, String logPath) {
        this.parent = parent;
        this.message = message == null ? "" : message;
        this.logTail = logTail == null ? "" : logTail;
        this.logPath = logPath == null ? "" : logPath;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(1, width / 2 - 110, height - 32, 100, 20,
                Lang.t("button.copy")));
        buttonList.add(new GuiButton(2, width / 2 + 10, height - 32, 100, 20,
                Lang.t("button.close")));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, Lang.t("error.title"), width / 2, 14, 0xFF5555);

        int left = width / 2 - 210;
        int maxWidth = 420;
        int y = 36;
        for (String line : wrap(message, maxWidth)) {
            drawString(fontRendererObj, line, left, y, 0xFFFFFF);
            y += 10;
        }

        if (!logTail.isEmpty()) {
            y += 6;
            drawString(fontRendererObj, Lang.t("error.logTail"), left, y, 0xA0A0A0);
            y += 12;
            Gui.drawRect(left - 4, y - 4, left + maxWidth + 4, height - 46, 0x90000000);
            for (String line : wrap(logTail, maxWidth)) {
                if (y > height - 50) {
                    break;
                }
                drawString(fontRendererObj, line, left, y, 0xBFBFBF);
                y += 10;
            }
        }

        if (!logPath.isEmpty()) {
            drawString(fontRendererObj, trim(Lang.f("error.logPath", logPath), maxWidth),
                    left, height - 44, 0x777777);
        }
        if (!status.isEmpty()) {
            drawCenteredString(fontRendererObj, status, width / 2, height - 44, 0x55D06B);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String trim(String text, int maxWidth) {
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

    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<String>();
        if (text == null) {
            return lines;
        }
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            String rest = paragraph;
            while (fontRendererObj.getStringWidth(rest) > maxWidth) {
                int fit = charIndexForWidth(rest, maxWidth);
                int space = rest.lastIndexOf(' ', Math.min(fit, rest.length() - 1));
                int cut = space > 0 ? space : Math.max(1, fit - 1);
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut).trim();
                if (rest.isEmpty()) {
                    break;
                }
            }
            lines.add(rest);
        }
        return lines;
    }

    private int charIndexForWidth(String text, int maxWidth) {
        int index = 0;
        while (index < text.length()
                && fontRendererObj.getStringWidth(text.substring(0, index + 1)) <= maxWidth) {
            index++;
        }
        return Math.max(1, index);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            close();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) {
            setClipboardString(buildReport());
            status = Lang.t("error.copied");
        } else if (button.id == 2) {
            close();
        }
    }

    private String buildReport() {
        StringBuilder sb = new StringBuilder();
        sb.append(message);
        if (!logTail.isEmpty()) {
            sb.append('\n').append("--- log ---").append('\n').append(logTail);
        }
        if (!logPath.isEmpty()) {
            sb.append('\n').append("log: ").append(logPath);
        }
        return sb.toString();
    }

    private void close() {
        mc.displayGuiScreen(parent != null ? parent : new GuiMainMenu());
    }
}
