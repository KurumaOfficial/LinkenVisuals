package ru.method.linkenvisuals.modules.impl.movement;


import java.util.ArrayList;
import java.util.List;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.screen.clickgui.ClickGui;
import ru.method.linkenvisuals.screen.linkgui.LinkGuiScreen;


public class ClickGuiWalk extends Module {

    public ClickGuiWalk() {
        super("ClickGuiWalk", Category.Movement);
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (fullNullCheck()) return;

        if (mc.currentScreen instanceof ClickGui clickGui) {
            if (!clickGui.getSearchBar().isFocused()) {
                updateMovementKeys();
            }
        } else if (mc.currentScreen instanceof LinkGuiScreen linkGui) {
            if (!linkGui.isSearchFocused()) {
                updateMovementKeys();
            }
        }
    }

    private void updateMovementKeys() {
        Sprint sprint = LinkenVisuals.getInstance().getModuleManager().getModule(Sprint.class);
        boolean shouldHandleSprint = sprint == null || !sprint.isToggled();

        List<KeyBinding> keys = new ArrayList<>();
        keys.add(mc.options.forwardKey);
        keys.add(mc.options.backKey);
        keys.add(mc.options.leftKey);
        keys.add(mc.options.rightKey);
        keys.add(mc.options.jumpKey);
        keys.add(mc.options.sneakKey);
        if (shouldHandleSprint) {
            keys.add(mc.options.sprintKey);
        }

        for (KeyBinding key : keys) {
            key.setPressed(isKeyBindingDown(key));
        }
    }

    private boolean isKeyBindingDown(KeyBinding keyBinding) {
        if (mc.getWindow() == null || keyBinding == null) return false;

        InputUtil.Key boundKey = InputUtil.fromTranslationKey(keyBinding.getBoundKeyTranslationKey());
        int code = boundKey.getCode();
        if (code < 0) return false;

        long handle = mc.getWindow().getHandle();
        if (boundKey.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(handle, code) == GLFW.GLFW_PRESS;
        }

        return InputUtil.isKeyPressed(mc.getWindow(), code);
    }
}
