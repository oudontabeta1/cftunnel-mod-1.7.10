package dev.oudon.cftunnel.client;

import dev.oudon.cftunnel.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Minimal file browser for selecting a service token file.
 */
public class GuiFilePicker extends GuiScreen {

    public interface Callback {
        void onFileSelected(File file);
    }

    private static final int ROW_HEIGHT = 14;

    private final GuiScreen parent;
    private final Callback callback;

    private final List<File> entries = new ArrayList<File>();
    private File directory;
    private int scroll;
    private GuiTextField pathField;
    private String error = "";

    public GuiFilePicker(GuiScreen parent, String startPath, Callback callback) {
        this.parent = parent;
        this.callback = callback;
        File start = startPath == null || startPath.trim().isEmpty()
                ? null : new File(startPath.trim());
        if (start != null && start.isFile()) {
            start = start.getParentFile();
        }
        this.directory = start != null && start.isDirectory()
                ? start : Minecraft.getMinecraft().mcDataDir;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        int left = width / 2 - 200;
        pathField = new GuiTextField(fontRendererObj, left, 32, 400, 18);
        pathField.setMaxStringLength(512);
        pathField.setText(directory.getAbsolutePath());

        buttonList.add(new GuiButton(1, width / 2 - 105, height - 32, 100, 20, Lang.t("pickup.up")));
        buttonList.add(new GuiButton(2, width / 2 + 5, height - 32, 100, 20, Lang.t("button.select")));
        buttonList.add(new GuiButton(3, width / 2 - 105, height - 56, 100, 20, Lang.t("button.cancel")));
        reload();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    private void reload() {
        entries.clear();
        File[] files = directory.listFiles();
        if (files != null) {
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    if (a.isDirectory() != b.isDirectory()) {
                        return a.isDirectory() ? -1 : 1;
                    }
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
            for (File file : files) {
                if (!file.getName().startsWith(".")) {
                    entries.add(file);
                }
            }
        }
        scroll = 0;
    }

    private int listTop() {
        return 56;
    }

    private int listBottom() {
        return height - 64;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, Lang.t("pickup.title"), width / 2, 16, 0xFFFFFF);
        pathField.drawTextBox();

        int left = width / 2 - 195;
        int right = width / 2 + 195;
        int rows = visibleRows();
        for (int i = 0; i < rows; i++) {
            int index = scroll + i;
            if (index >= entries.size()) {
                break;
            }
            File file = entries.get(index);
            int y = listTop() + i * ROW_HEIGHT;
            String name = file.getName() + (file.isDirectory() ? "/" : "");
            int color = file.isDirectory() ? 0xE0C060 : 0xFFFFFF;
            if (mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                Gui.drawRect(left - 2, y - 1, right + 2, y + ROW_HEIGHT - 1, 0x40FFFFFF);
                color = 0xFFFF80;
            }
            drawString(fontRendererObj, trim(name, 380), left, y, color);
            if (file.isFile()) {
                String size = humanSize(file.length());
                drawString(fontRendererObj, size, right - fontRendererObj.getStringWidth(size),
                        y, 0x707070);
            }
        }

        if (entries.size() > rows) {
            String more = (scroll + rows) + " / " + entries.size();
            drawString(fontRendererObj, more, right - fontRendererObj.getStringWidth(more),
                    listBottom() + 2, 0x707070);
        }

        if (!error.isEmpty()) {
            drawCenteredString(fontRendererObj, error, width / 2, height - 72, 0xFF5555);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        }
        return (bytes / (1024 * 1024)) + " MB";
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

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        pathField.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0 || mouseY < listTop() || mouseY > listBottom()) {
            return;
        }
        int index = scroll + (mouseY - listTop()) / ROW_HEIGHT;
        if (index < 0 || index >= entries.size()) {
            return;
        }
        File file = entries.get(index);
        if (file.isDirectory()) {
            directory = file;
            pathField.setText(file.getAbsolutePath());
            error = "";
            reload();
        } else {
            select(file);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int max = Math.max(0, entries.size() - visibleRows());
            scroll = Math.max(0, Math.min(max, scroll + (wheel > 0 ? -1 : 1)));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (keyCode == 28) {
            selectFromPath();
            return;
        }
        if (pathField.textboxKeyTyped(typedChar, keyCode)) {
            error = "";
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) {
            File parentDir = directory.getParentFile();
            if (parentDir != null) {
                directory = parentDir;
                pathField.setText(parentDir.getAbsolutePath());
                error = "";
                reload();
            }
        } else if (button.id == 2) {
            selectFromPath();
        } else if (button.id == 3) {
            mc.displayGuiScreen(parent);
        }
    }

    private void selectFromPath() {
        File target = new File(pathField.getText().trim());
        if (target.isFile()) {
            select(target);
        } else if (target.isDirectory()) {
            directory = target;
            error = "";
            reload();
        } else {
            error = Lang.t("pickup.notFound");
        }
    }

    private void select(File file) {
        callback.onFileSelected(file);
        mc.displayGuiScreen(parent);
    }
}
