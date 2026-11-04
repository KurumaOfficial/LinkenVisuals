package ru.method.linkenvisuals.screen.windowgui;

import lombok.experimental.UtilityClass;

/**
 * 1.21.11: fixed-function stencil state is gone. The stencil service is unused
 * by the redesigned screens (they clip via scissor), so this stays as a no-op
 * compatibility shim.
 */
@UtilityClass
public class WindowGuiStencil {
    public void initStencilToWrite() {
    }

    public void readStencilBuffer(int ref) {
    }

    public void uninitStencilBuffer() {
    }
}
