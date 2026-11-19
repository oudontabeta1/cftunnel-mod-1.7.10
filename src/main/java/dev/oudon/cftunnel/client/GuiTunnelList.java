package dev.oudon.cftunnel.client;

import dev.oudon.cftunnel.Lang;
import dev.oudon.cftunnel.ProfileStore;
import dev.oudon.cftunnel.TunnelManager;
import dev.oudon.cftunnel.TunnelProfile;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.util.List;

/**
 * 保存済みトンネルの一覧。ここから接続・追加・編集・削除・ログインを行う。
 */
public class GuiTunnelList extends GuiScreen {

    private static final int ROW_HEIGHT = 26;
    private static final int LIST_MARGIN = 200;

    private final GuiScreen parent;

    private int selected = -1;
    private int scroll;
    private boolean autoOpenEdit;
    private boolean deleteArmed;
    private long deleteArmedAt;
    private long lastClickAt;
    private int lastClickIndex = -1;
    private String notice = "";

    private GuiButton connectButton;
    private GuiButton addButton;
    private GuiButton editButton;
    private GuiButton deleteButton;
    private GuiButton loginButton;
    private GuiButton doneButton;

    public GuiTunnelList(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int center = width / 2;
        int row1 = height - 54;
        int row2 = height - 28;

        connectButton = new GuiButton(1, center - 80, row1, 160, 20, Lang.t("button.connect"));
        addButton = new GuiButton(2, center - 205, row2, 78, 20, Lang.t("button.add"));
        editButton = new GuiButton(3, center - 123, row2, 78, 20, Lang.t("button.edit"));
        deleteButton = new GuiButton(4, center - 41, row2, 78, 20, Lang.t("button.delete"));
        loginButton = new GuiButton(6, center + 41, row2, 78, 20, Lang.t("button.login"));
        doneButton = new GuiButton(5, center + 123, row2, 78, 20, Lang.t("button.done"));

        buttonList.add(connectButton);
        buttonList.add(addButton);
        buttonList.add(editButton);
        buttonList.add(deleteButton);
        buttonList.add(loginButton);
        buttonList.add(doneButton);

        if (selected < 0 && ProfileStore.get().size() > 0) {
            selected = 0;
        }
        autoOpenEdit = ProfileStore.get().size() == 0;
        clampScroll();
    }

    private int listLeft() {
        return width / 2 - LIST_MARGIN;
    }

    private int listRight() {
        return width / 2 + LIST_MARGIN;
    }

    private int listTop() {
        return 48;
    }

    private int listBottom() {
        return height - 62;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT);
    }

    private void clampScroll() {
        int max = Math.max(0, ProfileStore.get().size() - visibleRows());
        if (scroll > max) {
            scroll = max;
        }
        if (scroll < 0) {
            scroll = 0;
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (autoOpenEdit) {
            autoOpenEdit = false;
            mc.displayGuiScreen(new GuiTunnelEdit(this, -1));
            return;
        }
        List<TunnelProfile> profiles = ProfileStore.get().all();
        boolean hasSelection = selected >= 0 && selected < profiles.size();
        TunnelManager manager = TunnelManager.get();

        connectButton.enabled = hasSelection && !manager.isBusy();
        editButton.enabled = hasSelection;
        deleteButton.enabled = hasSelection;
        loginButton.enabled = hasSelection && !manager.isLoginRunning();
        deleteButton.displayString = deleteArmed
                ? Lang.t("button.deleteConfirm") : Lang.t("button.delete");

        if (deleteArmed && System.currentTimeMillis() - deleteArmedAt > 4000L) {
            deleteArmed = false;
        }
        if (manager.isLoginRunning()) {
            notice = manager.loginMessage();
        } else if (notice.equals(Lang.t("status.loginBrowser"))
                && !manager.loginMessage().isEmpty()) {
            notice = manager.loginMessage();
        }
        clampScroll();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, Lang.t("gui.title"), width / 2, 14, 0xFFFFFF);
        drawCenteredString(fontRendererObj, Lang.t("gui.subtitle"), width / 2, 28, 0x9E9E9E);

        List<TunnelProfile> profiles = ProfileStore.get().all();
        int left = listLeft();
        int right = listRight();
        int top = listTop();

        if (profiles.isEmpty()) {
            drawCenteredString(fontRendererObj, Lang.t("gui.empty"), width / 2,
                    (top + listBottom()) / 2, 0xA0A0A0);
        }

        TunnelManager manager = TunnelManager.get();
        int rows = visibleRows();
        for (int i = 0; i < rows; i++) {
            int index = scroll + i;
            if (index >= profiles.size()) {
                break;
            }
            TunnelProfile profile = profiles.get(index);
            int y = top + i * ROW_HEIGHT;
            boolean isSelected = index == selected;
            Gui.drawRect(left, y, right, y + ROW_HEIGHT - 2,
                    isSelected ? 0x90303030 : 0x60101010);
            if (isSelected) {
                Gui.drawRect(left, y, left + 2, y + ROW_HEIGHT - 2, 0xFF4A90D9);
            }
            drawString(fontRendererObj, trim(profile.displayName(), 190), left + 8, y + 4,
                    0xFFFFFF);
            drawString(fontRendererObj, trim(profile.hostnameOrEmpty(), 300), left + 8, y + 14,
                    0x9E9E9E);

            String state = "";
            if (manager.isBusy() && isSelected) {
                state = Lang.t("list.busy");
            } else if (manager.state() == TunnelManager.State.CONNECTED && isSelected) {
                state = Lang.t("list.connected");
            }
            if (!state.isEmpty()) {
                drawString(fontRendererObj, state, right - 8 - fontRendererObj.getStringWidth(state),
                        y + 9, 0x55D06B);
            }
        }

        if (profiles.size() > rows) {
            String more = Lang.f("list.more", Integer.valueOf(profiles.size() - rows - scroll));
            drawString(fontRendererObj, more, right - fontRendererObj.getStringWidth(more),
                    listBottom() - 10, 0x808080);
        }

        if (!notice.isEmpty()) {
            drawCenteredString(fontRendererObj, trim(notice, 380), width / 2, height - 68,
                    0xE0C060);
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
        if (mouseButton != 0) {
            return;
        }
        if (mouseX < listLeft() || mouseX > listRight()
                || mouseY < listTop() || mouseY > listBottom()) {
            return;
        }
        int index = scroll + (mouseY - listTop()) / ROW_HEIGHT;
        if (index < 0 || index >= ProfileStore.get().size()) {
            return;
        }
        if (index == lastClickIndex && System.currentTimeMillis() - lastClickAt < 300L) {
            selected = index;
            connect();
        } else {
            selected = index;
        }
        lastClickIndex = index;
        lastClickAt = System.currentTimeMillis();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll += wheel > 0 ? -1 : 1;
            clampScroll();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            mc.displayGuiScreen(parent != null ? parent : new GuiMainMenu());
            return;
        }
        if (keyCode == 200) {
            moveSelection(-1);
        } else if (keyCode == 208) {
            moveSelection(1);
        } else if (keyCode == 28) {
            connect();
        }
    }

    private void moveSelection(int delta) {
        int size = ProfileStore.get().size();
        if (size == 0) {
            return;
        }
        selected = Math.max(0, Math.min(size - 1, (selected < 0 ? 0 : selected) + delta));
        if (selected < scroll) {
            scroll = selected;
        } else if (selected >= scroll + visibleRows()) {
            scroll = selected - visibleRows() + 1;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 1:
                connect();
                break;
            case 2:
                mc.displayGuiScreen(new GuiTunnelEdit(this, -1));
                break;
            case 3:
                if (selected >= 0) {
                    mc.displayGuiScreen(new GuiTunnelEdit(this, selected));
                }
                break;
            case 4:
                deleteSelected();
                break;
            case 6:
                loginSelected();
                break;
            case 5:
                mc.displayGuiScreen(parent != null ? parent : new GuiMainMenu());
                break;
            default:
                break;
        }
    }

    private void connect() {
        TunnelProfile profile = ProfileStore.get().get(selected);
        if (profile == null || !profile.hasValidHostname()) {
            return;
        }
        if (TunnelManager.get().isBusy()) {
            return;
        }
        notice = "";
        TunnelManager.get().connect(this, profile);
        mc.displayGuiScreen(new GuiTunnelProgress(this, profile));
    }

    private void deleteSelected() {
        TunnelProfile profile = ProfileStore.get().get(selected);
        if (profile == null) {
            return;
        }
        if (!deleteArmed) {
            deleteArmed = true;
            deleteArmedAt = System.currentTimeMillis();
            return;
        }
        deleteArmed = false;
        ProfileStore.get().remove(selected);
        if (selected >= ProfileStore.get().size()) {
            selected = ProfileStore.get().size() - 1;
        }
        notice = Lang.t("list.deleted");
    }

    private void loginSelected() {
        TunnelProfile profile = ProfileStore.get().get(selected);
        if (profile == null) {
            return;
        }
        notice = Lang.t("status.loginBrowser");
        TunnelManager.get().loginAsync(profile, null);
    }
}
