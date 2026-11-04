package ru.method.linkenvisuals.screen.windowgui;

import lombok.experimental.UtilityClass;
import org.lwjgl.glfw.GLFW;

@UtilityClass
public class WindowGuiScreenUtil {
    public boolean isHovered(int mouseX, int mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void setHandCursor() {
        long window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        if (window != 0L) {
            GLFW.glfwSetCursor(window, GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR));
        }
    }

    public void setArrowCursor() {
        long window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        if (window != 0L) {
            GLFW.glfwSetCursor(window, GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR));
        }
    }
}
