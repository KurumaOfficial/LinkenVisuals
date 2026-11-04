package ru.method.linkenvisuals.modules.impl.movement;


import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.screen.clickgui.ClickGui;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;


public class MovementHelper extends Module {

    private int lastVerticalPressCode = -1;
    private int lastHorizontalPressCode = -1;

    public MovementHelper() {
        super("MovementHelper", Category.Movement);
    }

    @EventHandler
    public void onKey(EventKey e) {
        if (fullNullCheck() || mc.currentScreen != null || e.getAction() != 1) return;

        int keyCode = e.getKey();

        int forwardKeyCode = getBoundKeyboardCode(mc.options.forwardKey);
        int backKeyCode = getBoundKeyboardCode(mc.options.backKey);
        int leftKeyCode = getBoundKeyboardCode(mc.options.leftKey);
        int rightKeyCode = getBoundKeyboardCode(mc.options.rightKey);

        if (keyCode == forwardKeyCode || keyCode == backKeyCode) {
            lastVerticalPressCode = keyCode;
        }

        if (keyCode == leftKeyCode || keyCode == rightKeyCode) {
            lastHorizontalPressCode = keyCode;
        }
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (fullNullCheck()) return;

        ClickGuiWalk clickGuiWalk = LinkenVisuals.getInstance().getModuleManager().getModule(ClickGuiWalk.class);
        if (clickGuiWalk != null && clickGuiWalk.isToggled() && mc.currentScreen instanceof ClickGui) {
            ClickGui clickGui = (ClickGui) mc.currentScreen;
            if (!clickGui.getSearchBar().isFocused()) {
                return;
            }
        }

        if (mc.currentScreen != null) return;

        KeyBinding forward = mc.options.forwardKey;
        KeyBinding back = mc.options.backKey;
        KeyBinding left = mc.options.leftKey;
        KeyBinding right = mc.options.rightKey;

        restoreKeyState(forward);
        restoreKeyState(back);
        restoreKeyState(left);
        restoreKeyState(right);

        int forwardKeyCode = getBoundKeyboardCode(forward);
        int backKeyCode = getBoundKeyboardCode(back);
        int leftKeyCode = getBoundKeyboardCode(left);
        int rightKeyCode = getBoundKeyboardCode(right);

        if (forward.isPressed() && back.isPressed()) {
            if (lastVerticalPressCode == forwardKeyCode) {
                back.setPressed(false);
            } else if (lastVerticalPressCode == backKeyCode) {
                forward.setPressed(false);
            }
        }

        if (left.isPressed() && right.isPressed()) {
            if (lastHorizontalPressCode == leftKeyCode) {
                right.setPressed(false);
            } else if (lastHorizontalPressCode == rightKeyCode) {
                left.setPressed(false);
            }
        }
    }

    private void restoreKeyState(KeyBinding key) {
        if (mc.getWindow() == null || key == null) return;

        InputUtil.Key boundKey = InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey());
        int code = boundKey.getCode();
        if (code < 0) {
            key.setPressed(false);
            return;
        }

        long handle = mc.getWindow().getHandle();
        boolean isDown = boundKey.getCategory() == InputUtil.Type.MOUSE
                ? GLFW.glfwGetMouseButton(handle, code) == GLFW.GLFW_PRESS
                : InputUtil.isKeyPressed(mc.getWindow(), code);
        key.setPressed(isDown);
    }

    private int getBoundKeyboardCode(KeyBinding keyBinding) {
        if (keyBinding == null) return -1;
        InputUtil.Key boundKey = InputUtil.fromTranslationKey(keyBinding.getBoundKeyTranslationKey());
        if (boundKey.getCategory() == InputUtil.Type.MOUSE) return -1;
        return boundKey.getCode();
    }

    @Override
    public void onDisable() {
        lastVerticalPressCode = -1;
        lastHorizontalPressCode = -1;

        if (mc.options != null) {
            restoreKeyState(mc.options.forwardKey);
            restoreKeyState(mc.options.backKey);
            restoreKeyState(mc.options.leftKey);
            restoreKeyState(mc.options.rightKey);
        }

        super.onDisable();
    }
}
