package ru.method.linkenvisuals.api.mixins.accessors;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HandledScreen.class)
public interface IHandledScreen {
    @Accessor("focusedSlot")
    Slot getFocusedSlot();

    @Accessor("focusedSlot")
    @Mutable
    void setFocusedSlot(Slot slot);

    @Accessor("x")
    int getX();

    @Accessor("y")
    int getY();

    @Invoker("getSlotAt")
    Slot invokeGetSlotAt(double mouseX, double mouseY);
}
