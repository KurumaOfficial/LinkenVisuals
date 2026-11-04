package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.utils.inv.InventoryCursorLock;
import ru.method.linkenvisuals.api.mixins.accessors.IHandledScreen;
import ru.method.linkenvisuals.modules.impl.combat.AutoSwap;
import ru.method.linkenvisuals.modules.impl.misc.ItemScroller;
import ru.method.linkenvisuals.modules.impl.misc.ShulkerTooltip;
import ru.method.linkenvisuals.screen.inventory.InvOverlayRenderer;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow protected Slot focusedSlot;
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int backgroundWidth;

    // x/backgroundWidth читаем живыми каждый кадр — книга рецептов двигает окно после init().
    @Inject(method = "render", at = @At("TAIL"))
    private void linkenvisuals$invOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!((Object) this instanceof InventoryScreen screen)) return;
        InvOverlayRenderer.render(context, screen, x, y, backgroundWidth, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$invOverlayClick(Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (!((Object) this instanceof InventoryScreen screen)) return;
        if (InvOverlayRenderer.mouseClicked(screen, x, y, backgroundWidth, mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void linkenvisuals$maintainFrozenCursor(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!InventoryCursorLock.isFrozen()) return;
        InventoryCursorLock.maintain((HandledScreen<?>) (Object) this);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void linkenvisuals$auctionHelperPrepare(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ru.method.linkenvisuals.modules.impl.misc.AuctionHelper auction =
                LinkenVisuals.getInstance().getModuleManager().getModule(ru.method.linkenvisuals.modules.impl.misc.AuctionHelper.class);
        if (auction == null || !auction.isToggle()) return;
        auction.prepare((HandledScreen<?>) (Object) this);
    }

    // Подсветка аукциона рисуется ПОВЕРХ предметов на render TAIL (координаты абсолютные: x+slot.x).
    // Фон ЗА предметом (drawSlot HEAD) на кастомном GUI сервера был невидим — предмет заполняет слот.
    @Inject(method = "render", at = @At("TAIL"))
    private void linkenvisuals$auctionHelperOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ru.method.linkenvisuals.modules.impl.misc.AuctionHelper auction =
                LinkenVisuals.getInstance().getModuleManager().getModule(ru.method.linkenvisuals.modules.impl.misc.AuctionHelper.class);
        if (auction == null || !auction.isToggle()) return;
        auction.renderOverlay(context, x, y);
    }

    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$handleAutoSwapPick(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        AutoSwap autoSwap = LinkenVisuals.getInstance().getModuleManager().getModule(AutoSwap.class);
        if (autoSwap == null || !autoSwap.isAwaitingItemPick()) return;
        if (slot == null || !slot.hasStack()) return;

        autoSwap.acceptPickedItem(slot.getStack());
        ci.cancel();
    }

    @Inject(method = "drawMouseoverTooltip", at = @At("TAIL"))
    private void linkenvisuals$renderShulkerTooltip(DrawContext context, int x, int y, CallbackInfo ci) {
        ShulkerTooltip shulkerTooltip = LinkenVisuals.getInstance().getModuleManager().getModule(ShulkerTooltip.class);
        if (shulkerTooltip == null || !shulkerTooltip.isToggled()) return;
        shulkerTooltip.render(context, focusedSlot, x, y);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$pickAutoSwapItem(Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = click.x();
        double mouseY = click.y();
        AutoSwap autoSwap = LinkenVisuals.getInstance().getModuleManager().getModule(AutoSwap.class);
        if (autoSwap == null || !autoSwap.isAwaitingItemPick()) return;

        Slot slot = ((IHandledScreen) this).invokeGetSlotAt(mouseX, mouseY);
        if (slot == null || !slot.hasStack()) return;

        autoSwap.acceptPickedItem(slot.getStack());
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$beginItemScroller(Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        ItemScroller itemScroller = LinkenVisuals.getInstance().getModuleManager().getModule(ItemScroller.class);
        if (itemScroller == null || !itemScroller.isToggled()) return;
        ItemScroller.DragMode dragMode = itemScroller.resolveDragMode(button, linkenvisuals$shiftDown(), linkenvisuals$ctrlDown(), linkenvisuals$altDown());
        if (dragMode == ItemScroller.DragMode.NONE) return;

        Slot slot = ((IHandledScreen) this).invokeGetSlotAt(mouseX, mouseY);
        if (itemScroller.beginDragging((HandledScreen<?>) (Object) this, slot, mouseX, mouseY, dragMode) || itemScroller.isDragging()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$dragItemScroller(Click click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        ItemScroller itemScroller = LinkenVisuals.getInstance().getModuleManager().getModule(ItemScroller.class);
        if (itemScroller == null || !itemScroller.isToggled() || !itemScroller.isDragging() || button != 0) return;

        Slot slot = ((IHandledScreen) this).invokeGetSlotAt(mouseX, mouseY);
        itemScroller.dragOver((HandledScreen<?>) (Object) this, mouseX, mouseY, slot);
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$releaseItemScroller(Click click, CallbackInfoReturnable<Boolean> cir) {
        ItemScroller itemScroller = LinkenVisuals.getInstance().getModuleManager().getModule(ItemScroller.class);
        if (itemScroller == null || !itemScroller.isDragging() || click.button() != 0) return;

        itemScroller.stopDragging();
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$scrollItemScroller(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        ItemScroller itemScroller = LinkenVisuals.getInstance().getModuleManager().getModule(ItemScroller.class);
        if (itemScroller == null || !itemScroller.isToggled()) return;
        if (verticalAmount == 0.0D) return;

        Slot slot = ((IHandledScreen) this).invokeGetSlotAt(mouseX, mouseY);
        if (itemScroller.handleScroll(
                (HandledScreen<?>) (Object) this,
                slot,
                verticalAmount > 0,
                linkenvisuals$shiftDown(),
                linkenvisuals$ctrlDown(),
                linkenvisuals$altDown()
        )) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Закрытие ИГРОКОМ (E/ESC) идёт через HandledScreen.close(); закрытие сервером —
     * через CloseScreenS2CPacket → ClientPlayerEntity.closeScreen(), мимо этого метода.
     * Разница и позволяет сторожу раскладки открывать окно обратно только после сервера.
     */
    @Inject(method = "close", at = @At("HEAD"))
    private void linkenvisuals$playerClosedInventory(CallbackInfo ci) {
        if ((Object) this instanceof InventoryScreen) {
            ru.method.linkenvisuals.screen.inventory.InventorySetupManager.notifyPlayerClosedScreen();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void linkenvisuals$resetItemScrollerState(CallbackInfo ci) {
        ItemScroller itemScroller = LinkenVisuals.getInstance().getModuleManager().getModule(ItemScroller.class);
        if (itemScroller != null && itemScroller.isDragging()) {
            itemScroller.stopDragging();
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean linkenvisuals$keyDown(int key) {
        long window = net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, key) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean linkenvisuals$shiftDown() {
        return linkenvisuals$keyDown(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
            || linkenvisuals$keyDown(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean linkenvisuals$ctrlDown() {
        return linkenvisuals$keyDown(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
            || linkenvisuals$keyDown(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean linkenvisuals$altDown() {
        return linkenvisuals$keyDown(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT)
            || linkenvisuals$keyDown(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT);
    }
}
