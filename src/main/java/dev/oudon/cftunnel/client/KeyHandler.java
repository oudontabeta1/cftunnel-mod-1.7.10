package dev.oudon.cftunnel.client;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * Fallback key binding for opening the global Cloudflare settings.
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
        if (mc == null || mc.currentScreen instanceof GuiCfSettings) {
            return;
        }
        GuiScreen parent = mc.currentScreen;
        mc.displayGuiScreen(new GuiCfSettings(parent));
    }
}
