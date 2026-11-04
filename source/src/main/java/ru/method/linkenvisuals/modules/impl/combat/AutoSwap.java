package ru.method.linkenvisuals.modules.impl.combat;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import ru.method.linkenvisuals.api.events.impl.EventKey;
import ru.method.linkenvisuals.api.events.impl.EventMouse;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.api.mixins.accessors.IBoundKey;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;
import ru.method.linkenvisuals.modules.settings.impl.StringSetting;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.utils.client.ClientTasks;
import ru.method.linkenvisuals.utils.chat.ChatUtils;
import ru.method.linkenvisuals.utils.inv.InventoryCursorLock;
import ru.method.linkenvisuals.utils.inv.InventoryUtils;
import ru.method.linkenvisuals.utils.render.BorderRadius;
import ru.method.linkenvisuals.utils.render.ColorRGBA;
import ru.method.linkenvisuals.utils.render.ColorUtils;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.render.fonts.Fonts;
import lombok.AllArgsConstructor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PlayerHeadItem;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class AutoSwap extends Module {

    private final BindSetting swapBind = new BindSetting("settings.autoswap.key", new Bind(-1, false));
    private final EnumSetting<SwapMode> swapMode = new EnumSetting<>("settings.autoswap.mode", SwapMode.TwoItems);
    private final EnumSetting<ItemType> firstItem = new EnumSetting<>("settings.autoswap.firstitem", ItemType.Talisman, () -> swapMode.getValue() == SwapMode.TwoItems);
    private final EnumSetting<ItemType> secondItem = new EnumSetting<>("settings.autoswap.seconditem", ItemType.Sphere, () -> swapMode.getValue() == SwapMode.TwoItems);
    private final EnumSetting<WheelStyle> wheelStyle = new EnumSetting<>("settings.autoswap.wheel_style", WheelStyle.Classic, () -> swapMode.getValue() == SwapMode.MultipleItems);
    private final StringSetting[] multiSlots = new StringSetting[MULTI_SLOT_COUNT];


    private boolean bindHeld;
    private boolean wheelOpen;
    private boolean awaitingItemPick;
    private int pendingSlot = -1;
    private int cleanupCooldown;
    private boolean swapHeld;
    private int inventoryCloseTicks;
    private boolean temporaryInventoryOpened;
    private int itemPickGraceTicks;
    // Своп через открытие инвентаря: открыть → зафиксировать курсор на слоте → через пару тиков нажать F
    // (своп в оффхенд по наведённому слоту) → ещё пару тиков и закрыть.
    private int swapPressTicks;
    private int swapCloseTicks;
    // Экран, который открыл САМ модуль под своп. Сверяем по ссылке, чтобы F/закрытие не сработали по
    // чужому контейнеру (чест/шоп-GUI сервера), где номер слота значит другой предмет.
    private HandledScreen<?> swapScreen;

    private static final int MULTI_SLOT_COUNT = 5;
    private static final int INVENTORY_CLOSE_TICKS = 1;
    private static final int SWAP_OPEN_TO_PRESS_TICKS = 2;
    private static final int SWAP_PRESS_TO_CLOSE_TICKS = 2;
    private static final float WHEEL_RADIUS = 52f;
    private static final float SLOT_RADIUS = 13f;
    private static final float PLUS_FONT_SIZE = 10f;
    private static final float ITEM_TEXT_FONT_SIZE = 8.1f;
    private static final String SLOT_SEPARATOR = ";";
    private static final Identifier SNOW_TEXTURE = LinkenVisuals.id("client/particles/snow.png");
    private static final String ALLOWED_NAMES_FILE = "2.txt";
    private static final String ALLOWED_NAMES_RESOURCE = "/linkenvisuals/2.txt";
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("\u00a7x(?:\u00a7[0-9a-fA-F]){6}");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("\u00a7.");
    private final List<String> allowedNames = new ArrayList<>();
    private long allowedNamesLastModified = -1L;
    private boolean allowedNamesFromResource;

    public AutoSwap() {
        super("AutoSwap", Category.Combat);
        for (int i = 0; i < multiSlots.length; i++) {
            multiSlots[i] = new StringSetting("settings.autoswap.slot" + (i + 1), "", "", "", 256, () -> false, null);
            getSettings().add(multiSlots[i]);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        bindHeld = false;
        wheelOpen = false;
        awaitingItemPick = false;
        pendingSlot = -1;
        itemPickGraceTicks = 0;
        swapHeld = false;
        inventoryCloseTicks = 0;
        // Если модуль выключили, пока наш временный инвентарь открыт — закрываем его, иначе игрок
        // останется в GUI, который сам не открывал (onTick больше не тикает).
        if (temporaryInventoryOpened) {
            closeTemporaryInventory();
        }
        temporaryInventoryOpened = false;
        swapPressTicks = 0;
        swapCloseTicks = 0;
        swapScreen = null;
        InventoryCursorLock.unfreeze();
        if (mc != null && mc.mouse != null && mc.currentScreen == null) {
            mc.mouse.lockCursor();
        }
    }

    @EventHandler
    public void onKey(EventKey e) {
        if (fullNullCheck()) return;

        if (swapBind.getValue().matchesKey(e.getKey())) {
            handleBindAction(e.getAction() != 0);
        }
    }

    @EventHandler
    public void onMouse(EventMouse e) {
        if (fullNullCheck()) return;

        if (swapBind.getValue().matchesMouse(e.getButton())) {
            handleBindAction(e.getAction() != 0);
            return;
        }

        if (swapMode.getValue() == SwapMode.MultipleItems && wheelOpen && e.getAction() == 1) {
            handleWheelClick(e.getButton());
            e.consume();
        }
    }

    @EventHandler
    public void onRender2D(EventRender2D e) {
        if (swapMode.getValue() != SwapMode.MultipleItems || !wheelOpen) return;
        if (isSpectatorBlocked()) return;
        if (mc.currentScreen != null) return;
        renderWheel(e);
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player == null) return;
        if (InventoryCursorLock.isFrozen()) {
            if (swapScreen != null && mc.currentScreen == swapScreen) {
                InventoryCursorLock.maintain(swapScreen);
            } else {
                InventoryCursorLock.unfreeze();
            }
        }
        tickTemporaryInventory();
        tickInventorySwap();
        if (swapMode.getValue() != SwapMode.MultipleItems) return;

        if (itemPickGraceTicks > 0) {
            itemPickGraceTicks--;
        }
        if (awaitingItemPick && mc.currentScreen == null && itemPickGraceTicks <= 0) {
            awaitingItemPick = false;
            pendingSlot = -1;
            mc.mouse.lockCursor();
        }
        if (wheelOpen && isSpectatorBlocked()) {
            wheelOpen = false;
            bindHeld = false;
            if (mc.currentScreen == null) {
                mc.mouse.lockCursor();
            }
        }
        // Пока колесо открыто — держим курсор разлоченным. Ванильный Mouse возвращает захват сам
        // (например, при клике мышью, когда не открыт ни один экран), из-за чего курсор пропадал.
        // unlockCursor() безопасно вызывать каждый тик: если курсор уже разлочен, он ничего не делает.
        if (wheelOpen && mc.currentScreen == null) {
            mc.mouse.unlockCursor();
        }

        cleanupCooldown++;
        if (cleanupCooldown < 10) return;
        cleanupCooldown = 0;

        for (StringSetting multiSlot : multiSlots) {
            String encoded = multiSlot.getValue();
            if (encoded.isEmpty()) continue;
            if (!hasItemByEncoded(encoded)) {
                multiSlot.setValue("");
            }
        }
    }

    public boolean isAwaitingItemPick() {
        return awaitingItemPick;
    }

    public boolean acceptPickedItem(ItemStack stack) {
        if (swapMode.getValue() != SwapMode.MultipleItems) {
            return false;
        }
        if (!awaitingItemPick || pendingSlot < 0 || pendingSlot >= multiSlots.length) {
            return false;
        }
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        String encoded = encodeItem(stack);
        if (isSlotDuplicate(encoded, pendingSlot)) {
            ChatUtils.sendMessage(Text.literal("Автосвап: Этот предмет уже добавлен").formatted(Formatting.RED));
            return false;
        }
        multiSlots[pendingSlot].setValue(encoded);
        awaitingItemPick = false;
        pendingSlot = -1;
        mc.setScreen(null);
        return true;
    }

    private void handleBindAction(boolean pressed) {
        if (swapMode.getValue() == SwapMode.TwoItems) {
            if (pressed && !swapHeld && mc.currentScreen == null && !isCreativeBlocked()) {
                swapHeld = true;
                performSwap();
            } else if (!pressed) {
                swapHeld = false;
            }
            return;
        }

        if (isSpectatorBlocked()) {
            wheelOpen = false;
            bindHeld = false;
            return;
        }

        if (pressed) {
            if (bindHeld) return;
            if (mc.currentScreen != null || awaitingItemPick) return;
            if (isCreativeBlocked()) return;
            bindHeld = true;
            wheelOpen = true;
            mc.mouse.unlockCursor();
        } else {
            if (!bindHeld) return;
            bindHeld = false;
            if (!awaitingItemPick && wheelOpen) {
                int slotIndex = getWheelSlotIndex();
                if (slotIndex != -1) {
                    String encoded = multiSlots[slotIndex].getValue();
                    if (!encoded.isEmpty() && !isOffhandEncoded(encoded) && !isCreativeBlocked()) {
                        performMultiSwap(slotIndex);
                    }
                }
            }
            if (!awaitingItemPick) {
                wheelOpen = false;
                if (mc.currentScreen == null) {
                    mc.mouse.lockCursor();
                }
            }
        }
    }

    private void handleWheelClick(int button) {
        if (isSpectatorBlocked()) return;
        int slotIndex = getWheelSlotIndex();
        if (slotIndex < 0) return;

        if (button == 1) {
            if (!multiSlots[slotIndex].getValue().isEmpty()) {
                multiSlots[slotIndex].setValue("");
            }
            return;
        }

        if (button != 0 && button != 2) return;

        String encoded = multiSlots[slotIndex].getValue();
        if (button == 2) {
            if (!encoded.isEmpty() && !isOffhandEncoded(encoded) && !isCreativeBlocked()) {
                performMultiSwap(slotIndex);
            }
            return;
        }

        if (encoded.isEmpty()) {
            awaitingItemPick = true;
            pendingSlot = slotIndex;
            itemPickGraceTicks = 5;
            wheelOpen = false;
            if (mc.player != null && mc.player.isCreative()) {
                mc.setScreen(new CreativeInventoryScreen(mc.player, mc.world.getEnabledFeatures(), true));
            } else {
                mc.setScreen(new InventoryScreen(mc.player));
            }
            return;
        }

        if (!isOffhandEncoded(encoded) && !isCreativeBlocked()) {
            performMultiSwap(slotIndex);
        }
        if (!bindHeld) {
            wheelOpen = false;
        }
    }

    private void renderWheel(EventRender2D e) {
        float centerX = mc.getWindow().getScaledWidth() / 2f;
        float centerY = mc.getWindow().getScaledHeight() / 2f;
        float baseRadius = WHEEL_RADIUS + SLOT_RADIUS + 6f;
        float innerRadius = Math.max(8f, WHEEL_RADIUS - SLOT_RADIUS - 6f);
        java.awt.Color theme = ColorUtils.getGlobalColor();
        java.awt.Color softTheme = ColorUtils.mix(theme, java.awt.Color.WHITE, 0.35f);
        boolean glass = wheelStyle.getValue() == WheelStyle.LiquidGlass;
        java.awt.Color baseBack = glass ? ColorUtils.getGlassColor(255) : ColorUtils.getGlassColor(170);
        java.awt.Color innerBack = glass ? ColorUtils.getGlassColor(255) : ColorUtils.getGlassColor(140);
        java.awt.Color outline = glass
                ? ColorUtils.alpha(ColorUtils.getGlassOutlineColor(), 150)
                : new java.awt.Color(softTheme.getRed(), softTheme.getGreen(), softTheme.getBlue(), 90);
        java.awt.Color glow = glass
                ? ColorUtils.alpha(ColorUtils.getGlassHighlightColor(), 90)
                : new java.awt.Color(softTheme.getRed(), softTheme.getGreen(), softTheme.getBlue(), 40);

        if (glass) {
            float baseSize = baseRadius * 2f;
            float baseX = centerX - baseRadius;
            float baseY = centerY - baseRadius;
            Render2D.drawLiquidRect(
                    e.getContext().getMatrices(),
                    baseX,
                    baseY,
                    baseSize,
                    baseSize,
                    new BorderRadius(baseRadius),
                    new ColorRGBA(baseBack),
                    2.5f,
                    22.0f,
                    11f,
                    220.0f,
                    true,
                    0f,
                    0.19f
            );
            float innerSize = innerRadius * 2f;
            float innerX = centerX - innerRadius;
            float innerY = centerY - innerRadius;
            Render2D.drawLiquidRect(
                    e.getContext().getMatrices(),
                    innerX,
                    innerY,
                    innerSize,
                    innerSize,
                    new BorderRadius(innerRadius),
                    new ColorRGBA(innerBack),
                    2.5f,
                    22.0f,
                    11f,
                    220.0f,
                    true,
                    0f,
                    0.19f
            );
            Render2D.drawCircle(e.getContext().getMatrices(), centerX, centerY, baseRadius - 0.6f, outline);
            Render2D.drawCircle(e.getContext().getMatrices(), centerX, centerY, innerRadius + 0.6f, ColorUtils.alpha(ColorUtils.getGlassOutlineColor(), 140));
        } else {
            Render2D.drawCircle(e.getContext().getMatrices(), centerX, centerY, baseRadius, baseBack);
            Render2D.drawCircle(e.getContext().getMatrices(), centerX, centerY, baseRadius - 1.4f, ColorUtils.getGlassColor(120));
            Render2D.drawCircle(e.getContext().getMatrices(), centerX, centerY, innerRadius, innerBack);
            Render2D.drawCircle(e.getContext().getMatrices(), centerX, centerY, innerRadius - 1.0f, ColorUtils.getGlassColor(100));
        }
        // soft glow ring
        Render2D.drawCircle(e.getContext().getMatrices(), centerX, centerY, baseRadius + 4.6f, new java.awt.Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 30));
        Render2D.drawCircle(e.getContext().getMatrices(), centerX, centerY, baseRadius + 3.2f, new java.awt.Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 40));
        Render2D.drawCircle(e.getContext().getMatrices(), centerX, centerY, baseRadius + 2.2f, glow);
        Render2D.drawCircle(e.getContext().getMatrices(), centerX, centerY, baseRadius + 0.8f, outline);

        float mouseX = getScaledMouseX();
        float mouseY = getScaledMouseY();
        int hovered = getWheelSlotIndex(mouseX, mouseY, centerX, centerY);
        ItemStack offhandStack = mc.player == null ? ItemStack.EMPTY : mc.player.getOffHandStack();
        String offhandEncoded = offhandStack.isEmpty() ? "" : encodeItem(offhandStack);
        int slotCount = multiSlots.length;

        long timeMs = System.currentTimeMillis();
        for (int i = 0; i < slotCount; i++) {
            float boundaryAngle = -MathHelper.HALF_PI + (MathHelper.TAU / slotCount) * i + (MathHelper.TAU / slotCount) / 2f;
            float startX = centerX + (float) Math.cos(boundaryAngle) * innerRadius;
            float startY = centerY + (float) Math.sin(boundaryAngle) * innerRadius;
            float endX = centerX + (float) Math.cos(boundaryAngle) * (baseRadius - 3f);
            float endY = centerY + (float) Math.sin(boundaryAngle) * (baseRadius - 3f);
            Render2D.drawLine(e.getContext().getMatrices(), startX, startY, endX, endY, 0.9f, new ru.method.linkenvisuals.utils.render.ColorRGBA(255, 255, 255, 28));
        }

        float snowRadius = innerRadius + (baseRadius - innerRadius) * 0.55f;
        int snowCount = 6;
        float snowTime = (timeMs % 8000L) / 8000f;
        for (int i = 0; i < snowCount; i++) {
            float angle = -MathHelper.HALF_PI + (MathHelper.TAU / snowCount) * i + snowTime * MathHelper.TAU;
            float snowX = centerX + (float) Math.cos(angle) * snowRadius;
            float snowY = centerY + (float) Math.sin(angle) * snowRadius;
            float size = 6f;
            Render2D.drawTexture(
                    e.getContext().getMatrices(),
                    snowX - size / 2f,
                    snowY - size / 2f,
                    size,
                    size,
                    2f,
                    SNOW_TEXTURE,
                    new java.awt.Color(255, 255, 255, 200)
            );
        }

        for (int i = 0; i < slotCount; i++) {
            float angle = -MathHelper.HALF_PI + (MathHelper.TAU / slotCount) * i;
            float slotX = centerX + (float) Math.cos(angle) * WHEEL_RADIUS;
            float slotY = centerY + (float) Math.sin(angle) * WHEEL_RADIUS;

            boolean isHovered = i == hovered;
            String encoded = multiSlots[i].getValue();
            boolean isOffhand = !encoded.isEmpty() && encoded.equals(offhandEncoded);
            java.awt.Color slotBg = glass
                    ? ColorUtils.getGlassColor(isHovered ? 210 : 175)
                    : (isHovered
                    ? new java.awt.Color(40, 40, 40, 170)
                    : new java.awt.Color(25, 25, 25, 150));
            if (glass) {
                float slotSize = SLOT_RADIUS * 2f;
                Render2D.drawLiquidRect(
                        e.getContext().getMatrices(),
                        slotX - SLOT_RADIUS,
                        slotY - SLOT_RADIUS,
                        slotSize,
                        slotSize,
                        new BorderRadius(SLOT_RADIUS),
                        new ColorRGBA(slotBg),
                        2.5f,
                        18.0f,
                        11f,
                        220.0f,
                        true,
                        0f,
                        0.19f
                );
                Render2D.drawCircle(e.getContext().getMatrices(), slotX, slotY, SLOT_RADIUS - 0.4f, ColorUtils.alpha(ColorUtils.getGlassOutlineColor(), 150));
            } else {
                Render2D.drawCircle(e.getContext().getMatrices(), slotX, slotY, SLOT_RADIUS, slotBg);
            }
            java.awt.Color ringColor;
            if (isOffhand) {
                ringColor = new java.awt.Color(235, 70, 70, 210);
            } else if (isHovered) {
                ringColor = new java.awt.Color(235, 205, 85, 210);
            } else {
                ringColor = glass
                        ? ColorUtils.alpha(ColorUtils.getGlassOutlineColor(), 150)
                        : ColorUtils.getGlassColor(110);
            }
            Render2D.drawCircle(e.getContext().getMatrices(), slotX, slotY, SLOT_RADIUS - 1.2f, ringColor);

            if (encoded.isEmpty()) {
                String plus = "+";
                float plusWidth = Fonts.SEMIBOLD.getWidth(plus, PLUS_FONT_SIZE);
                float plusHeight = Fonts.SEMIBOLD.getHeight(PLUS_FONT_SIZE);
                Render2D.drawFont(e.getContext().getMatrices(), Fonts.SEMIBOLD.getFont(PLUS_FONT_SIZE), plus, slotX - plusWidth / 2f, slotY - plusHeight / 2f, new java.awt.Color(255, 255, 255, 220));
            } else {
                ItemStack preview = getPreviewStack(encoded);
                if (!preview.isEmpty()) {
                    int itemX = Math.round(slotX - 8f);
                    int itemY = Math.round(slotY - 8f);
                    e.getContext().drawItem(preview, itemX, itemY);
                }
            }
        }

        if (hovered != -1) {
            String encoded = multiSlots[hovered].getValue();
            String title = "+";
            if (!encoded.isEmpty()) {
                ItemStack preview = getPreviewStack(encoded);
                if (!preview.isEmpty()) {
                    title = cleanDisplayName(preview.getName().getString());
                }
            }
            title = Render2D.stripObfuscatedFormatting(title);
            float fontSize = ITEM_TEXT_FONT_SIZE;
            float textWidth = Fonts.MEDIUM.getWidth(title, fontSize);
            float textHeight = Fonts.MEDIUM.getHeight(fontSize);
            float boxW = textWidth + 10f;
            float boxH = textHeight + 6f;
            float boxX = centerX - boxW / 2f;
            float boxY = centerY - boxH / 2f;
            if (glass) {
                Render2D.drawLiquidRect(
                        e.getContext().getMatrices(),
                        boxX,
                        boxY,
                        boxW,
                        boxH,
                        new BorderRadius(4f),
                        new ColorRGBA(ColorUtils.getGlassColor(255)),
                        2.5f,
                        18.0f,
                        11f,
                        220.0f,
                        true,
                        0f,
                        0.19f
                );
                Render2D.drawRoundedRect(e.getContext().getMatrices(), boxX, boxY, boxW, boxH, 4f, ColorUtils.alpha(ColorUtils.getGlassOutlineColor(), 150));
            } else {
                Render2D.drawRoundedRect(e.getContext().getMatrices(), boxX, boxY, boxW, boxH, 3f, new java.awt.Color(0, 0, 0, 140));
            }
            Render2D.drawFont(e.getContext().getMatrices(), Fonts.MEDIUM.getFont(fontSize), title, centerX - textWidth / 2f, centerY - textHeight / 2f + 1f, new java.awt.Color(255, 255, 255, 230));
        }
    }

    private boolean isOffhandEncoded(String encoded) {
        if (encoded == null || encoded.isEmpty()) return false;
        if (mc.player == null) return false;
        ItemStack offhandStack = mc.player.getOffHandStack();
        return !offhandStack.isEmpty() && encoded.equals(encodeItem(offhandStack));
    }

    private String cleanDisplayName(String name) {
        if (name == null || name.isEmpty()) return "";
        String cleaned = name.replaceAll("\\[[^\\]]*\\]\\s*", "");
        cleaned = cleaned.replace("[", "").replace("]", "");
        cleaned = cleaned.replaceAll("^(?:123\\s*)+", "").replaceAll("(?:\\s*123)+$", "");
        return cleaned.trim();
    }

    private boolean isCreativeBlocked() {
        return mc.player != null && mc.player.isCreative();
    }

    private boolean isSpectatorBlocked() {
        return mc.player != null && mc.player.isSpectator();
    }

    private boolean isSlotDuplicate(String encoded, int excludeIndex) {
        if (encoded == null || encoded.isEmpty()) return false;
        for (int i = 0; i < multiSlots.length; i++) {
            if (i == excludeIndex) continue;
            if (encoded.equals(multiSlots[i].getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasItemByEncoded(String encoded) {
        if (encoded == null || encoded.isEmpty()) return false;
        if (isOffhandEncoded(encoded)) return true;
        return findItemByEncoded(encoded) != -1;
    }

    private int getWheelSlotIndex() {
        float mouseX = getScaledMouseX();
        float mouseY = getScaledMouseY();
        float centerX = mc.getWindow().getScaledWidth() / 2f;
        float centerY = mc.getWindow().getScaledHeight() / 2f;
        return getWheelSlotIndex(mouseX, mouseY, centerX, centerY);
    }

    private int getWheelSlotIndex(float mouseX, float mouseY, float centerX, float centerY) {
        // Радиальный выбор: НЕ требуем попасть ровно в кружок предмета — достаточно навести «рядом»
        // (в его сторону). За мёртвой зоной у центра выбираем слот, ближайший по УГЛУ к курсору.
        float dx = mouseX - centerX;
        float dy = mouseY - centerY;
        float dist2 = dx * dx + dy * dy;
        float deadZone = WHEEL_RADIUS * 0.35f;
        if (dist2 < deadZone * deadZone) {
            return -1; // у центра выбор не делаем — иначе дрожь переключала бы слоты
        }
        float mouseDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
        int best = -1;
        float bestDelta = Float.MAX_VALUE;
        for (int i = 0; i < multiSlots.length; i++) {
            float slotDeg = -90f + (360f / multiSlots.length) * i;
            float delta = Math.abs(MathHelper.wrapDegrees(mouseDeg - slotDeg));
            if (delta < bestDelta) {
                bestDelta = delta;
                best = i;
            }
        }
        return best;
    }

    private float getScaledMouseX() {
        return (float) (mc.mouse.getX() * mc.getWindow().getScaledWidth() / mc.getWindow().getWidth());
    }

    private float getScaledMouseY() {
        return (float) (mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight());
    }

    private void performMultiSwap(int slotIndex) {
        if (fullNullCheck()) return;
        if (isCreativeBlocked()) return;
        if (slotIndex < 0 || slotIndex >= multiSlots.length) return;

        String encoded = multiSlots[slotIndex].getValue();
        int slot = findItemByEncoded(encoded);
        if (slot == -1) {
            ChatUtils.sendMessage(Text.literal("\u0410\u0432\u0442\u043e\u0441\u0432\u0430\u043f: \u041f\u0440\u0435\u0434\u043c\u0435\u0442 \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d").formatted(Formatting.RED));
            return;
        }

        ItemStack offHandItem = mc.player.getOffHandStack();
        ItemStack targetStack = mc.player.getInventory().getStack(slot);
        Text offhandName = resolveDisplayNameText(offHandItem, Text.literal(""));
        Text safeTargetName = resolveDisplayNameText(targetStack, Text.literal("\u043d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u043e"));
        float oldSpeed = mc.player.getMovementSpeed();
        mc.player.setMovementSpeed(0);
        swapWithOffhand(slot);
        clearCursor();
        mc.player.setMovementSpeed(oldSpeed);
        sendSwapMessage(offhandName, safeTargetName);
    }

    private void sendSwapMessage(Text offhandName, Text targetName) {
        MutableText message = Text.literal("Сменил ")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xE8EEF7)));
        if (offhandName != null && !offhandName.getString().trim().isEmpty()) {
            message.append(offhandName.copy())
                    .append(Text.literal(" на ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
        } else {
            message.append(Text.literal(" на ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
        }
        message.append(targetName == null
                ? Text.literal("неизвестно").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF7A7A)))
                : targetName.copy());
        showSwapActionBar(message);
    }

    private void showSwapActionBar(Text message) {
        Runnable action = () -> mc.inGameHud.setOverlayMessage(message, false);
        if (mc.isOnThread()) {
            action.run();
        } else {
            mc.execute(action);
        }
    }

    private String encodeItem(ItemStack stack) {
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        String name = stack.getCustomName() != null ? stack.getName().getString() : "";
        return encodePart(id) + SLOT_SEPARATOR + encodePart(name);
    }

    private ItemStack buildPreviewStack(String encoded) {
        if (encoded == null || encoded.isEmpty()) return ItemStack.EMPTY;
        String[] parts = splitEncoded(encoded);
        if (parts.length == 0) return ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(decodePart(parts[0]));
        if (id == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(Registries.ITEM.get(id));
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (parts.length > 1 && !parts[1].isEmpty()) {
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(decodePart(parts[1])));
        }
        return stack;
    }

    private ItemStack getPreviewStack(String encoded) {
        if (encoded == null || encoded.isEmpty()) return ItemStack.EMPTY;
        if (mc.player != null) {
            ItemStack offhandStack = mc.player.getOffHandStack();
            if (!offhandStack.isEmpty() && encoded.equals(encodeItem(offhandStack))) {
                return offhandStack;
            }

            int slot = findItemByEncoded(encoded);
            if (slot != -1) {
                ItemStack stack = mc.player.getInventory().getStack(slot);
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
        }
        return buildPreviewStack(encoded);
    }

    private int findItemByEncoded(String encoded) {
        if (encoded == null || encoded.isEmpty()) return -1;
        String[] parts = splitEncoded(encoded);
        if (parts.length == 0) return -1;

        Identifier id = Identifier.tryParse(decodePart(parts[0]));
        if (id == null) return -1;

        boolean requireName = parts.length > 1 && !parts[1].isEmpty();
        String name = requireName ? decodePart(parts[1]) : "";

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (!Registries.ITEM.getId(stack.getItem()).equals(id)) continue;
            if (!requireName) return i;
            if (stack.getCustomName() != null && stack.getName().getString().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private String[] splitEncoded(String encoded) {
        List<String> parts = new ArrayList<>(2);
        StringBuilder current = new StringBuilder(encoded.length());
        boolean escaped = false;
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == SLOT_SEPARATOR.charAt(0)) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        parts.add(current.toString());
        return parts.toArray(new String[0]);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace(SLOT_SEPARATOR, "\\" + SLOT_SEPARATOR);
    }

    private String encodePart(String value) {
        String safe = value == null ? "" : value;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(safe.getBytes(StandardCharsets.UTF_8));
    }

    private String decodePart(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }

    private void performSwap() {
        if (fullNullCheck()) return;
        if (isCreativeBlocked()) return;

        ItemStack offHandItem = mc.player.getOffHandStack();

        if (offHandItem.isEmpty()) {
            ChatUtils.sendMessage(Text.literal("Автосвап: в оффхенде ничего нет").formatted(Formatting.RED));
            return;
        }

        ItemType offhandType = null;
        ItemType targetType = null;

        if (matchesItemType(offHandItem, firstItem.getValue())) {
            offhandType = firstItem.getValue();
            targetType = secondItem.getValue();
        } else if (matchesItemType(offHandItem, secondItem.getValue())) {
            offhandType = secondItem.getValue();
            targetType = firstItem.getValue();
        }

        if (offhandType == null || targetType == null) {
            ChatUtils.sendMessage(Text.literal("\u0410\u0432\u0442\u043e\u0441\u0432\u0430\u043f: \u041d\u0435\u0447\u0435\u0433\u043e \u0441\u0432\u0430\u043f\u0430\u0442\u044c").formatted(Formatting.RED));
            return;
        }

        if (firstItem.getValue() == secondItem.getValue()) {
            int slot = findItemByType(targetType);
            if (slot == -1) {
                if (hasItemByType(targetType)) {
                    ChatUtils.sendMessage(Text.literal("\u0410\u0432\u0442\u043e\u0441\u0432\u0430\u043f: \u041f\u0440\u0435\u0434\u043c\u0435\u0442 \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d").formatted(Formatting.RED));
                } else {
                    ChatUtils.sendMessage(Text.literal("\u0410\u0432\u0442\u043e\u0441\u0432\u0430\u043f: \u041d\u0435\u0447\u0435\u0433\u043e \u0441\u0432\u0430\u043f\u0430\u0442\u044c").formatted(Formatting.RED));
                }
                return;
            }
        }

        final Text finalOffhandName = resolveDisplayNameText(offHandItem, Text.literal(""));
        final ItemType finalTargetType = targetType;
        final String finalTargetFallback = getItemTypeFallbackName(targetType).getString();

        ClientTasks.schedule(2, () -> {
            if (fullNullCheck()) return;

            int slot = findItemByType(finalTargetType);
            if (slot != -1) {
                ItemStack targetStack = mc.player.getInventory().getStack(slot);
                Text resolvedTargetName = resolveDisplayNameText(targetStack, Text.literal(finalTargetFallback));
                float oldSpeed = mc.player.getMovementSpeed();
                mc.player.setMovementSpeed(0);
                swapWithOffhand(slot);
                clearCursor();
                mc.player.setMovementSpeed(oldSpeed);
                sendSwapMessage(finalOffhandName, resolvedTargetName);
                return;
            }

            if (hasItemByType(finalTargetType)) {
                ChatUtils.sendMessage(Text.literal("\u0410\u0432\u0442\u043e\u0441\u0432\u0430\u043f: \u041f\u0440\u0435\u0434\u043c\u0435\u0442 \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d").formatted(Formatting.RED));
            } else {
                ChatUtils.sendMessage(Text.literal("\u0410\u0432\u0442\u043e\u0441\u0432\u0430\u043f: \u041d\u0435\u0447\u0435\u0433\u043e \u0441\u0432\u0430\u043f\u0430\u0442\u044c").formatted(Formatting.RED));
            }
        });
    }

    private boolean matchesItemType(ItemStack stack, ItemType type) {
        switch (type) {
            case Talisman -> {
                return isTalisman(stack);
            }
            case Sphere -> {
                return isSphere(stack);
            }
        }
        return false;
    }

    private int findItemByType(ItemType type) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (matchesItemType(stack, type) && isAllowedDisplayName(stack)) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasItemByType(ItemType type) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (matchesItemType(stack, type)) {
                return true;
            }
        }
        return false;
    }

    private Text getItemTypeFallbackName(ItemType type) {
        return switch (type) {
            case Talisman -> Text.literal("\u0422\u0430\u043b\u0438\u0441\u043c\u0430\u043d");
            case Sphere -> Text.literal("\u0421\u0444\u0435\u0440\u0430");
        };
    }

    private boolean isTalisman(ItemStack stack) {
        return !stack.isEmpty() &&
                stack.getItem() == Items.TOTEM_OF_UNDYING &&
                hasAttributes(stack);
    }

    private boolean isSphere(ItemStack stack) {
        return !stack.isEmpty() &&
                stack.getItem() instanceof PlayerHeadItem &&
                hasAttributes(stack) &&
                !hasCustomPlayerHeadName(stack);
    }

    private boolean hasAttributes(ItemStack stack) {
        AttributeModifiersComponent attributes = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        return attributes != null && !attributes.modifiers().isEmpty();
    }

    private boolean hasCustomPlayerHeadName(ItemStack stack) {
        String name = stack.getName().getString();
        String prefix = "\u0413\u043e\u043b\u043e\u0432\u0430 \u0438\u0433\u0440\u043e\u043a\u0430";
        return name.startsWith(prefix) && name.length() > prefix.length();
    }

    private boolean isAllowedDisplayName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getCustomName() == null) return true;
        if (swapMode.getValue() == SwapMode.MultipleItems && (isSphere(stack) || isTalisman(stack) || isSphereOrTalismanBase(stack))) {
            return true;
        }
        return isAllowedDisplayName(stack.getName().getString());
    }

    private Text resolveDisplayNameText(ItemStack stack, Text emptyFallback) {
        if (stack == null || stack.isEmpty()) return emptyFallback;
        Text name = stack.getName().copy();
        if (name.getString().trim().isEmpty()) {
            Text itemName = stack.getItem().getName().copy();
            if (!itemName.getString().trim().isEmpty()) {
                return itemName;
            }
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            if (id != null && !id.trim().isEmpty()) {
                return Text.literal(id);
            }
            return emptyFallback;
        }
        return name;
    }

    private boolean isSphereOrTalismanBase(ItemStack stack) {
        return stack.getItem() == Items.TOTEM_OF_UNDYING || stack.getItem() instanceof PlayerHeadItem;
    }

    private boolean isAllowedDisplayName(String name) {
        if (swapMode.getValue() == SwapMode.TwoItems) return true;
        String normalized = normalizeName(name);
        if (normalized.isEmpty()) return false;
        loadAllowedNames();
        if (allowedNames.isEmpty()) return true;
        return allowedNames.contains(normalized);
    }

    private void loadAllowedNames() {
        File file = new File(mc.runDirectory, ALLOWED_NAMES_FILE);
        if (!file.exists()) {
            file = new File(LinkenVisuals.getInstance().getGlobalsDir(), ALLOWED_NAMES_FILE);
        }

        if (file.exists()) {
            long modified = file.lastModified();
            if (modified == allowedNamesLastModified && !allowedNamesFromResource) return;
            allowedNames.clear();
            allowedNamesLastModified = modified;
            allowedNamesFromResource = false;
            try {
                List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                parseAllowedNames(lines);
                return;
            } catch (Exception ignored) {
                allowedNames.clear();
            }
        }

        if (allowedNamesFromResource) return;
        allowedNames.clear();
        allowedNamesLastModified = -1L;
        try {
            List<String> lines = readAllowedNamesFromResource();
            parseAllowedNames(lines);
            allowedNamesFromResource = !allowedNames.isEmpty();
        } catch (Exception ignored) {
            allowedNames.clear();
        }
    }

    private List<String> readAllowedNamesFromResource() throws Exception {
        if (ALLOWED_NAMES_RESOURCE == null) return List.of();
        try (var stream = AutoSwap.class.getResourceAsStream(ALLOWED_NAMES_RESOURCE)) {
            if (stream == null) return List.of();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
                return lines;
            }
        }
    }

    private void parseAllowedNames(List<String> lines) {
        String last = null;
        for (String line : lines) {
            String cleaned = stripFormatting(line);
            if (cleaned.isEmpty()) continue;
            String lower = cleaned.toLowerCase(Locale.ROOT);
            if ("аура".equals(lower) || "пыль".equals(lower) || "заморозка".equals(lower)) {
                if (last != null) {
                    last = last + " " + cleaned;
                    continue;
                }
            }
            if (last != null) {
                addAllowedName(last);
            }
            last = cleaned;
        }
        if (last != null) {
            addAllowedName(last);
        }
    }

    private void addAllowedName(String name) {
        String normalized = normalizeName(name);
        if (normalized.isEmpty()) return;
        if (!allowedNames.contains(normalized)) {
            allowedNames.add(normalized);
        }
        if (normalized.startsWith("[★]")) {
            String withoutStar = normalizeName(normalized.replace("[★]", "").trim());
            if (!withoutStar.isEmpty() && !allowedNames.contains(withoutStar)) {
                allowedNames.add(withoutStar);
            }
        }
    }

    private String normalizeName(String name) {
        String cleaned = stripFormatting(name);
        if (cleaned.isEmpty()) return "";
        return cleaned.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String stripFormatting(String name) {
        if (name == null) return "";
        String cleaned = HEX_COLOR_PATTERN.matcher(name).replaceAll("");
        cleaned = COLOR_CODE_PATTERN.matcher(cleaned).replaceAll("");
        return cleaned.trim();
    }

    private void swapWithOffhand(int slot) {
        if (slot == -1) return;
        if (swapPressTicks > 0 || swapCloseTicks > 0) return; // наш своп уже идёт — не входим повторно
        int inventorySlot = InventoryUtils.indexToSlot(slot);

        // Инвентарь игрока уже открыт вручную — свапаем нажатием swapHandsKey на слоте синхронно (как
        // было). ТОЛЬКО для своего InventoryScreen: в чужом контейнере номер слота значит другой предмет.
        if (mc.currentScreen instanceof InventoryScreen manual) {
            InventoryCursorLock.freeze(manual, inventorySlot);
            InputUtil.Key key = ((IBoundKey) mc.options.swapHandsKey).getBoundKey();
            manual.keyPressed(new net.minecraft.client.input.KeyInput(key.getCode(), 0, 0));
            InventoryCursorLock.unfreeze();
            return;
        }
        // Открыт любой другой экран (чест/шоп/GUI сервера) — не трогаем, чтобы не свапнуть чужой слот.
        if (mc.currentScreen != null) return;

        if (mc.player.isCreative()) {
            InventoryUtils.clickPlayerHandlerSlot(inventorySlot, 40, SlotActionType.SWAP);
            return;
        }

        // Экрана нет: открываем инвентарь, фиксируем курсор на слоте (InventoryCursorLock — держит
        // focusedSlot и варпит курсор каждый тик), через пару тиков нажимаем F (своп наведённого слота
        // ↔ оффхенд), потом закрываем. Легитимнее прямого клика по syncId=0 без открытого экрана.
        mc.setScreen(new InventoryScreen(mc.player));
        if (mc.currentScreen instanceof HandledScreen<?> opened) {
            swapScreen = opened;
            InventoryCursorLock.freeze(opened, inventorySlot);
            temporaryInventoryOpened = true;
            swapPressTicks = SWAP_OPEN_TO_PRESS_TICKS;
        } else {
            InventoryUtils.clickPlayerHandlerSlot(inventorySlot, 40, SlotActionType.SWAP);
        }
    }

    // Тик-машина свопа через открытый инвентарь: ждём пару тиков → жмём F → ждём пару тиков → закрываем.
    // На каждом шаге сверяем, что открыт именно НАШ экран (по ссылке) — иначе прерываемся, чтобы не
    // нажать F по чужому контейнеру и не закрыть экран, который игрок открыл сам.
    private void tickInventorySwap() {
        if (swapPressTicks > 0) {
            if (mc.currentScreen != swapScreen || !(mc.currentScreen instanceof HandledScreen<?> screen)) {
                abortInventorySwap();
                return;
            }
            swapPressTicks--;
            if (swapPressTicks <= 0) {
                InputUtil.Key key = ((IBoundKey) mc.options.swapHandsKey).getBoundKey();
                screen.keyPressed(new net.minecraft.client.input.KeyInput(key.getCode(), 0, 0));
                clearCursor();
                swapCloseTicks = SWAP_PRESS_TO_CLOSE_TICKS;
            }
            return;
        }
        if (swapCloseTicks > 0) {
            if (mc.currentScreen != swapScreen) {
                abortInventorySwap();
                return;
            }
            swapCloseTicks--;
            if (swapCloseTicks <= 0) {
                closeTemporaryInventory();
            }
        }
    }

    private void abortInventorySwap() {
        swapPressTicks = 0;
        swapCloseTicks = 0;
        InventoryCursorLock.unfreeze();
        temporaryInventoryOpened = false;
        swapScreen = null;
    }

    private void openTemporaryInventory() {
        temporaryInventoryOpened = false;
        if (mc.player.isCreative()) {
            inventoryCloseTicks = 0;
            return;
        }
        if (mc.currentScreen == null) {
            mc.setScreen(new InventoryScreen(mc.player));
            inventoryCloseTicks = INVENTORY_CLOSE_TICKS;
            temporaryInventoryOpened = true;
        }
    }

    private void tickTemporaryInventory() {
        if (inventoryCloseTicks <= 0) return;
        if (!(mc.currentScreen instanceof InventoryScreen)) {
            inventoryCloseTicks = 0;
            temporaryInventoryOpened = false;
            return;
        }
        clearCursor();
        if (hasCursorStack()) {
            return;
        }
        inventoryCloseTicks--;
        if (inventoryCloseTicks <= 0) {
            closeTemporaryInventory();
        }
    }

    private void clearCursor() {
        if (mc.player.playerScreenHandler.getCursorStack() != null &&
                !mc.player.playerScreenHandler.getCursorStack().isEmpty()) {
            for (int i = 0; i < 36; i++) {
                if (mc.player.getInventory().getStack(i).isEmpty()) {
                    int slot = InventoryUtils.indexToSlot(i);
                    InventoryUtils.clickPlayerHandlerSlot(slot, 0, SlotActionType.PICKUP);
                    break;
                }
            }
        }
    }

    private boolean hasCursorStack() {
        return mc.player != null
                && mc.player.playerScreenHandler != null
                && !mc.player.playerScreenHandler.getCursorStack().isEmpty();
    }

    private void closeTemporaryInventory() {
        InventoryCursorLock.unfreeze();
        if (mc.player != null) {
            mc.player.closeHandledScreen();
        }
        mc.setScreen(null);
        temporaryInventoryOpened = false;
        swapScreen = null;
    }

    @AllArgsConstructor
    public enum ItemType implements Nameable {
        Sphere("settings.autoswap.sphere"),
        Talisman("settings.autoswap.talik");

        private final String name;

        @Override
        public String getName() {
            return name;
        }

        public Text getDisplayName() {
            return switch (this) {
                case Talisman -> Text.literal("Талисман");
                case Sphere -> Text.literal("Сфера");
            };
        }
    }

    @AllArgsConstructor
    public enum SwapMode implements Nameable {
        TwoItems("settings.autoswap.mode.two"),
        MultipleItems("settings.autoswap.mode.multi");

        private final String name;

        @Override
        public String getName() {
            return name;
        }
    }

    @AllArgsConstructor
    public enum WheelStyle implements Nameable {
        Classic("settings.autoswap.wheel_style.classic"),
        LiquidGlass("settings.autoswap.wheel_style.glass");

        private final String name;

        @Override
        public String getName() {
            return name;
        }
    }
}
