package dev.oudon.cftunnel.client;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * ボタンが他 MOD と重なった場合の逃げ道としてキーバインドも用意する。
 */
public class KeyHandler {

    public static final KeyBinding OPEN = new KeyBinding(
            "key.cftunnel.open", Keyboard.KEY_NONE, "key.categories.cftunnel");

    public static void register() {
        ClientRegistry.registerKeyBinding(OPEN);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!Keyboard.getEventKeyState() || OPEN.getKeyCode() == Keyboard.KEY_NONE) {
            return;
        }
        if (Keyboard.getEventKey() != OPEN.getKeyCode()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        if (mc.currentScreen instanceof GuiTunnelList || mc.currentScreen instanceof GuiTunnelEdit
                || mc.currentScreen instanceof GuiTunnelProgress
                || mc.currentScreen instanceof GuiTunnelError) {
            return;
        }
        mc.displayGuiScreen(new GuiTunnelList(mc.currentScreen));
    }
}
