package ru.method.linkenvisuals.modules.impl.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.impl.NumberSetting;
import ru.method.linkenvisuals.utils.text.streamer.StreamerModePatterns;

/**
 * Клиентский «дюп»: пока модуль включён, в инвентаре лежит призрачный Талисман Крушителя —
 * его видишь только ты, сервер о нём не знает.
 *
 * <p>Модуль следит за общим КОЛИЧЕСТВОМ талисманов, а не за конкретным слотом: сервер регулярно
 * ресинкает инвентарь и без восстановления предмет пропадал бы после первого же клика. Раскладывать
 * и перетаскивать его при этом можно свободно.</p>
 *
 * <p>Выброшенный предмет становится клиентской сущностью: её можно подобрать обратно, а если рядом
 * пройдёт другой игрок — она визуально улетит к нему (у него, разумеется, не изменится ничего).</p>
 *
 * <p>{@code /ah sell <цена>} перехватывается при вводе и на сервер НЕ уходит. Сразу печатается
 * «выставлен на продажу», предмет пропадает из инвентаря, а через 10 секунд приходит
 * «У Вас купили…» и на ту же сумму подрастают монеты в сайдбаре.</p>
 */
public class FakeCosmetic extends Module {

    private static final String ITEM_NAME = "Талисман Крушителя";
    private static final int SELL_DELAY = 200; // 10 секунд

    /** {@code ah sell 5000} / {@code ah sell 1,000,000} / {@code ah sell 5k} — слэш уже отрезан. */
    private static final Pattern AH_SELL = Pattern.compile(
            "^ah\\s+sell\\s+([\\d][\\d .,]*)\\s*(ккк|kkk|кк|kk|мм|mm|м|m|к|k)?",
            Pattern.CASE_INSENSITIVE);

    private final NumberSetting quantity = new NumberSetting("settings.fakecosmetic.quantity", 1f, 1f, 64f, 1f);

    /** Задержка подбора после выброса — как у ванильного дропа, иначе предмет прыгает обратно в руку. */
    private static final int PICKUP_DELAY = 40;
    /** Радиус подбора (блоки) — примерно как у ванильного игрока. */
    private static final double PICKUP_RANGE = 1.4;

    /**
     * Выставленные лоты. Именно список, а не одна пара «цена/количество»: выставив второй предмет, не
     * дождавшись первого, ты затирал первую продажу и сообщение о ней не приходило вовсе.
     */
    private final List<PendingSale> pending = new ArrayList<>();

    /** Один выставленный лот: цена, размер и сколько тиков осталось до «покупки». */
    private static final class PendingSale {
        final long price;
        final int count;
        int ticksLeft = SELL_DELAY;

        PendingSale(long price, int count) {
            this.price = price;
            this.count = count;
        }
    }
    /** Сколько талисманов сейчас «в инвентаре»: уменьшается при выбросе, растёт при подборе. */
    private int heldCount;
    /** Последнее значение ползунка — по его смене стак выдаётся заново. */
    private int lastQuantity = -1;
    /** Выброшенные призраки, живущие только на клиенте. */
    private final List<DroppedGhost> dropped = new ArrayList<>();
    /** Отрицательные id, чтобы не столкнуться с серверными сущностями. */
    private static int nextFakeId = -900_000;

    /** Выброшенный стак + собственный счётчик возраста (поле задержки у ItemEntity приватное). */
    private static final class DroppedGhost {
        final net.minecraft.entity.ItemEntity entity;
        int age;

        DroppedGhost(net.minecraft.entity.ItemEntity entity) {
            this.entity = entity;
        }
    }
    /** Сколько «наторговано» — прибавляется к монетам в сайдбаре. Статик: читается из миксина рендера. */
    private static volatile long bonusCoins;

    public FakeCosmetic() {
        super("SecretDupe", Category.Other);
        getSettings().add(quantity);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        pending.clear();
        bonusCoins = 0;
        lastQuantity = -1;
        heldCount = 0;
    }

    @Override
    public void onDisable() {
        // Возвращаем инвентарь и сайдбар серверному состоянию, иначе призраки останутся висеть.
        removeGhostEverywhere();
        pending.clear();
        bonusCoins = 0;
        lastQuantity = -1;
        heldCount = 0;
        super.onDisable();
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) return;

        syncQuantity();
        tickDropped();
        holdGhost();

        // Каждый лот отсчитывает свои 10 секунд независимо от остальных.
        var sales = pending.iterator();
        while (sales.hasNext()) {
            PendingSale sale = sales.next();
            if (--sale.ticksLeft > 0) continue;
            bonusCoins += sale.price; // монеты в сайдбаре растут ровно в момент «покупки»
            sendSoldMessage(sale.price, sale.count);
            sales.remove();
        }
    }

    /** Убирает призрачный стак отовсюду — из инвентаря и из выброшенных на землю. */
    private void removeGhostEverywhere() {
        for (DroppedGhost drop : dropped) drop.entity.discard();
        dropped.clear();
        heldCount = 0;
        if (fullNullCheck()) return;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (isGhost(inv.getStack(i))) inv.setStack(i, ItemStack.EMPTY);
        }
    }

    /** Ползунок подвинули — выдаём стак заново, всё выброшенное считаем неактуальным. */
    private void syncQuantity() {
        int wanted = Math.round(quantity.getValue());
        if (wanted == lastQuantity) return;
        for (DroppedGhost drop : dropped) drop.entity.discard();
        dropped.clear();
        lastQuantity = wanted;
        heldCount = wanted;
    }

    /**
     * Ведёт выброшенные стаки: сущность живёт только на клиенте, поэтому возраст и подбор считаем сами
     * (поле задержки подбора у {@code ItemEntity} приватное).
     */
    private void tickDropped() {
        if (dropped.isEmpty()) return;

        var iterator = dropped.iterator();
        while (iterator.hasNext()) {
            DroppedGhost drop = iterator.next();
            var entity = drop.entity;

            if (entity.isRemoved() || entity.getEntityWorld() != mc.world) { // смена мира/выгрузка
                iterator.remove();
                continue;
            }

            // Ванильный ItemEntity при ударе о землю отражает скорость по Y (умножает на -0.5) и подпрыгивает.
            // Гасим ровно этот подскок и ничего больше: горизонтальную скорость оставляем нетронутой, иначе
            // ItemPhysics теряет наклон и вращение — прошлая версия зануляла всё и ломала анимацию укладки.
            var velocity = entity.getVelocity();
            if (entity.isOnGround() && velocity.y > 0) {
                entity.setVelocity(velocity.x, 0, velocity.z);
            }

            if (++drop.age < PICKUP_DELAY) continue;

            // Чужой игрок подошёл — предмет «улетает» к нему. Анимация локальная: у него в инвентаре
            // ничего не появится, он этот предмет вообще не видит, сервер о нём не знает.
            var collector = nearbyOtherPlayer(entity);
            if (collector != null) {
                playPickup(entity, collector);
                entity.discard();
                iterator.remove();
                continue;
            }

            if (entity.squaredDistanceTo(mc.player) > PICKUP_RANGE * PICKUP_RANGE) continue;

            int free = 64 - heldCount;
            if (free <= 0) continue; // рука полная — предмет лежит и ждёт

            int taken = Math.min(free, entity.getStack().getCount());

            // Анимацию заводим ДО списания стака: частица делает копию сущности в момент создания, и если
            // стак уже опустошён — копия пустая, лететь нечему и видна одна тень.
            playPickup(entity, mc.player);

            heldCount += taken;
            entity.getStack().decrement(taken);

            if (entity.getStack().isEmpty()) {
                entity.discard();
                iterator.remove();
            }
        }
    }

    /** Ванильная анимация подбора: предмет летит к подобравшему, плюс звук с его позиции. */
    private void playPickup(net.minecraft.entity.ItemEntity entity, net.minecraft.entity.Entity collector) {
        try {
            var state = mc.getEntityRenderDispatcher().getAndUpdateRenderState(entity, 0f);
            if (state != null) {
                mc.particleManager.addParticle(new net.minecraft.client.particle.ItemPickupParticle(
                        mc.world, state, collector, collector.getVelocity()));
            }
        } catch (Throwable ignored) {
        }
        mc.world.playSound(mc.player, collector.getX(), collector.getY(), collector.getZ(),
                net.minecraft.sound.SoundEvents.ENTITY_ITEM_PICKUP,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.2f,
                ((mc.player.getRandom().nextFloat() - mc.player.getRandom().nextFloat()) * 0.7f + 1f) * 2f);
    }

    /**
     * Клик по слоту в открытом инвентаре. Если в деле участвует призрак — ванильный обработчик отменяем.
     *
     * <p>Причина: {@code clickSlot} отправляет серверу пакет о содержимом слота. Сервер видит там совсем
     * не то, что мы нарисовали, считает клиент рассинхронизированным и присылает ресинк всего инвентаря —
     * призрак стирается, и предмет «пропадает» прямо под курсором. Заодно это защищает настоящие вещи:
     * без отмены сервер выполнил бы клик по своей версии слота и перемешал бы реальный инвентарь.</p>
     *
     * <p>Сам клик применяем локально через {@code onSlotClick} — визуально всё работает как обычно.</p>
     *
     * @return {@code true}, если клик обработан нами и ванильный вызов надо отменить.
     */
    public static boolean handleSlotClick(int slotId, int button,
                                          net.minecraft.screen.slot.SlotActionType actionType,
                                          net.minecraft.entity.player.PlayerEntity player) {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return false;

        FakeCosmetic module = instance.getModuleManager().getModule(FakeCosmetic.class);
        if (module == null || !module.isToggled()) return false;
        if (player == null || player.currentScreenHandler == null) return false;

        var handler = player.currentScreenHandler;
        boolean cursorIsGhost = module.isGhost(handler.getCursorStack());
        boolean slotIsGhost = slotId >= 0 && slotId < handler.slots.size()
                && module.isGhost(handler.getSlot(slotId).getStack());
        if (!cursorIsGhost && !slotIsGhost) return false;

        // Выброс за пределы окна. Строго PICKUP: раскладывание перетаскиванием (QUICK_CRAFT) шлёт свои
        // служебные фазы с тем же slotId == -999, и без этой проверки стак «выбрасывался» прямо в момент,
        // когда его разносили по слотам.
        if (slotId == -999 && cursorIsGhost && actionType == net.minecraft.screen.slot.SlotActionType.PICKUP) {
            ItemStack cursor = handler.getCursorStack();
            int amount = button == 1 ? 1 : cursor.getCount(); // ПКМ вне окна выбрасывает один предмет
            for (int i = 0; i < amount; i++) module.spawnDroppedGhost();
            cursor.decrement(amount);
            handler.setCursorStack(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
            module.heldCount = Math.max(0, module.heldCount - amount);
            return true;
        }

        // Q / Ctrl+Q по слоту в открытом инвентаре — тоже выброс, но из слота, а не с курсора.
        if (slotIsGhost && actionType == net.minecraft.screen.slot.SlotActionType.THROW) {
            ItemStack stack = handler.getSlot(slotId).getStack();
            int amount = button == 1 ? stack.getCount() : 1;
            for (int i = 0; i < amount; i++) module.spawnDroppedGhost();
            stack.decrement(amount);
            if (stack.isEmpty()) handler.getSlot(slotId).setStack(ItemStack.EMPTY);
            module.heldCount = Math.max(0, module.heldCount - amount);
            return true;
        }

        handler.onSlotClick(slotId, button, actionType, player);
        return true;
    }

    /** Ближайший к предмету чужой игрок в радиусе подбора, либо {@code null}. */
    private net.minecraft.entity.player.PlayerEntity nearbyOtherPlayer(net.minecraft.entity.ItemEntity entity) {
        net.minecraft.entity.player.PlayerEntity best = null;
        double bestDistance = PICKUP_RANGE * PICKUP_RANGE;
        for (var player : mc.world.getPlayers()) {
            if (player == mc.player || player.isSpectator() || !player.isAlive()) continue;
            double distance = entity.squaredDistanceTo(player);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    /**
     * Q / Ctrl+Q по слоту с призраком. Ванильный выброс отменяем (сервер о предмете не знает и отправка
     * пакета уронила бы то, что лежит в этом слоте у него), вместо него кладём на землю клиентскую сущность.
     *
     * @return {@code true}, если выброс наш и ванильный обработчик надо отменить.
     */
    public static boolean handleDrop(boolean entireStack) {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return false;

        FakeCosmetic module = instance.getModuleManager().getModule(FakeCosmetic.class);
        if (module == null || !module.isToggled()) return false;
        return module.dropGhost(entireStack);
    }

    private boolean dropGhost(boolean entireStack) {
        if (fullNullCheck() || mc.world == null) return false;

        var inv = mc.player.getInventory();
        ItemStack inHand = inv.getStack(inv.getSelectedSlot());
        if (!isGhost(inHand)) return false; // в руке не наш предмет — не мешаем
        if (heldCount <= 0) return false;

        // Всегда ровно один: стакающихся тотемов в игре не существует, и пачка на земле выдала бы подделку.
        // Ctrl+Q поэтому тоже кидает один, а не весь стак.
        int amount = 1;
        heldCount -= amount;

        // Списываем из руки сразу: holdGhost сверяет сумму по инвентарю и иначе вернул бы предмет обратно.
        inHand.decrement(amount);
        if (inHand.isEmpty()) inv.setStack(inv.getSelectedSlot(), ItemStack.EMPTY);

        spawnDroppedGhost();
        return true;
    }

    /** Кладёт на землю ровно один призрачный талисман — бросок повторяет ванильный выброс игроком. */
    private void spawnDroppedGhost() {
        if (fullNullCheck() || mc.world == null) return;

        var player = mc.player;
        var entity = new net.minecraft.entity.ItemEntity(mc.world,
                player.getX(), player.getEyeY() - 0.3, player.getZ(), createTalisman(1));
        entity.setId(nextFakeId--);
        entity.setUuid(java.util.UUID.randomUUID());

        // Бросок вперёд — те же множители, что у ванильного ItemEntity при выбросе игроком.
        float yaw = player.getYaw() * ((float) Math.PI / 180f);
        float pitch = player.getPitch() * ((float) Math.PI / 180f);
        entity.setVelocity(
                -net.minecraft.util.math.MathHelper.sin(yaw) * net.minecraft.util.math.MathHelper.cos(pitch) * 0.3,
                -net.minecraft.util.math.MathHelper.sin(pitch) * 0.3 + 0.1,
                net.minecraft.util.math.MathHelper.cos(yaw) * net.minecraft.util.math.MathHelper.cos(pitch) * 0.3);

        mc.world.addEntity(entity);
        dropped.add(new DroppedGhost(entity));
    }

    /**
     * Поддерживает нужное КОЛИЧЕСТВО талисманов в инвентаре, не привязываясь к конкретному слоту.
     *
     * <p>Раньше стак принудительно переписывался в один и тот же слот каждый тик — из-за этого предмет
     * нельзя было перетащить: он мгновенно возвращался на место. Теперь модуль считает только общую
     * сумму: разложил по слотам, объединил, переставил — всё сохраняется, а восстанавливается лишь то,
     * что съел ресинк сервера.</p>
     *
     * <p>Пока открыт экран с инвентарём, не трогаем вообще ничего, иначе стак вырывается из-под курсора.</p>
     */
    private void holdGhost() {
        if (mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen) return;

        var inv = mc.player.getInventory();
        int total = countGhosts(inv);

        if (total == heldCount) return;
        if (total > heldCount) { // игрок сам что-то намудрил — доверяем инвентарю
            heldCount = total;
            return;
        }

        int missing = heldCount - total;

        // Сначала доливаем в уже лежащие стаки, чтобы не плодить слоты после каждого ресинка.
        for (int i = 0; i < inv.size() && missing > 0; i++) {
            ItemStack stack = inv.getStack(i);
            if (!isGhost(stack)) continue;
            int add = Math.min(missing, 64 - stack.getCount());
            if (add <= 0) continue;
            stack.setCount(stack.getCount() + add);
            missing -= add;
        }

        while (missing > 0) {
            int slot = firstFreeSlot(inv);
            if (slot < 0) return; // инвентарь забит — молча ждём
            int add = Math.min(missing, 64);
            inv.setStack(slot, createTalisman(add));
            missing -= add;
        }
    }

    private int countGhosts(net.minecraft.entity.player.PlayerInventory inv) {
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (isGhost(stack)) total += stack.getCount();
        }
        return total;
    }

    private int firstFreeSlot(net.minecraft.entity.player.PlayerInventory inv) {
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private boolean isGhost(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.TOTEM_OF_UNDYING)) return false;
        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        return name != null && name.getString().contains(ITEM_NAME);
    }

    /**
     * Игрок отправил {@code /ah sell <цена>}. Зовётся из перехвата ввода в чате (ChatScreenHintsMixin)
     * и из отправки команд (ClientPlayNetworkHandlerMixin) — на случай макросов.
     *
     * @return {@code true}, если команда наша: тогда её НЕ надо отправлять на сервер, всё остаётся локально.
     */
    public static boolean noteCommand(String command) {
        if (command == null) return false;

        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) return false;

        FakeCosmetic module = instance.getModuleManager().getModule(FakeCosmetic.class);
        if (module == null || !module.isToggled()) return false;

        String c = command.trim();
        while (c.startsWith("/")) c = c.substring(1).trim();

        Matcher matcher = AH_SELL.matcher(c);
        if (!matcher.find()) return false;

        long price = parsePrice(matcher.group(1), matcher.group(2));
        if (price <= 0) return false;

        // Продаётся РОВНО то, что в руке. Раньше считался весь инвентарь и одна команда сносила все
        // разложенные по слотам талисманы разом.
        var inv = mc.player.getInventory();
        ItemStack inHand = inv.getStack(inv.getSelectedSlot());
        if (!module.isGhost(inHand)) return false; // в руке не талисман — команда уходит на сервер как обычная

        int count = inHand.getCount();
        module.pending.add(new PendingSale(price, count));

        inv.setStack(inv.getSelectedSlot(), ItemStack.EMPTY); // лот ушёл на аукцион — из руки предмет пропадает
        module.heldCount = Math.max(0, module.heldCount - count);

        module.sendListedMessage(count);
        return true;
    }

    // ------------------------------------------------------------- монеты в сайдбаре

    /**
     * Дорисовывает «наторгованное» к числу монет в строке сайдбара — сервер продолжает слать своё
     * значение, мы поверх показываем своё.
     *
     * <p>Зовётся из двух точек, потому что сайдбар рисуют два разных пути: {@code InGameHudMixin}
     * (ванильный — на конструкторе {@code SidebarEntry}, а НЕ на аксессоре {@code name()}: рендер
     * читает поле записи через getfield и аксессор не вызывает) и {@code ScoreboardHud} (собственный
     * HUD клиента).</p>
     *
     * <p>Стили сохраняются: строка разбирается на куски с уже разрешёнными стилями через
     * {@link Text#visit}, подменяется только сам числовой отрезок.</p>
     */
    public static Text boostCoins(Text line) {
        long bonus = bonusCoins;
        if (bonus <= 0 || line == null) return line;

        List<String> parts = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        line.visit((style, part) -> {
            parts.add(part);
            styles.add(style);
            return java.util.Optional.empty();
        }, Style.EMPTY);

        String flat = String.join("", parts);
        Matcher matcher = StreamerModePatterns.COIN_LABEL_PATTERN.matcher(flat);
        if (!matcher.find()) return line;

        int from = matcher.start(2);
        int to = matcher.end(2);
        // В шаблоне группа числа — (\d[\d\s.,_]*), то есть в неё попадает и пробел ПОСЛЕ числа
        // («Монет: 10,100 всего»). Сдвигаем правую границу на последнюю цифру, иначе хвост съедается.
        while (to > from && !Character.isDigit(flat.charAt(to - 1))) to--;

        String digits = flat.substring(from, to);
        String cleaned = digits.replaceAll("[^\\d]", "");
        if (cleaned.isEmpty()) return line;

        long value;
        try {
            value = Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            return line;
        }

        // Разделитель берём тот же, что у сервера, иначе «10,100» превратится в «24888».
        char separator = digits.indexOf(',') >= 0 ? ','
                : digits.indexOf(' ') >= 0 ? ' '
                : digits.indexOf('.') >= 0 ? '.' : 0;
        String replacement = group(value + bonus, separator);

        MutableText out = Text.empty();
        int cursor = 0;
        boolean inserted = false;
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            int start = cursor;
            int end = cursor + part.length();
            cursor = end;

            if (end <= from || start >= to) { // кусок целиком вне числа
                append(out, part, styles.get(i));
                continue;
            }

            append(out, part.substring(0, Math.max(0, from - start)), styles.get(i));
            if (!inserted) {
                append(out, replacement, styles.get(i));
                inserted = true;
            }
            if (to < end) append(out, part.substring(to - start), styles.get(i));
        }
        return out;
    }

    private static void append(MutableText target, String text, Style style) {
        if (!text.isEmpty()) target.append(Text.literal(text).setStyle(style));
    }

    /** 24888 + разделитель ',' → «24,888»; разделитель 0 → «24888». */
    private static String group(long value, char separator) {
        String raw = Long.toString(value);
        if (separator == 0) return raw;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            if (i > 0 && (raw.length() - i) % 3 == 0) sb.append(separator);
            sb.append(raw.charAt(i));
        }
        return sb.toString();
    }

    /** «1,000,000» / «1 000 000» / «5k» / «2кк» → число. */
    private static long parsePrice(String digits, String suffix) {
        String cleaned = digits.replaceAll("[ .,]", "");
        long value;
        try {
            value = Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            return -1;
        }
        if (suffix == null) return value;
        return switch (suffix.toLowerCase()) {
            case "к", "k" -> value * 1_000L;
            case "кк", "kk", "м", "m" -> value * 1_000_000L;
            case "ккк", "kkk", "мм", "mm" -> value * 1_000_000_000L;
            default -> value;
        };
    }

    // Цвета серверных сообщений аукциона. Спиговые «§x§F§F§6§6§0§0» ванильный клиент не понимает
    // (у него нет кода 'x') — локально их надо собирать компонентами, иначе в чат летит мусор.
    private static final int C_MARK = 0xFF6600;  // [☃]
    private static final int C_TEXT = 0xFFFFE6;  // обычный текст
    private static final int C_ITEM = 0xFFFF00;  // название в «выставлен»
    private static final int C_MONEY = 0x00FF00; // цена

    /** «Предмет %item% x64 выставлен на продажу!» — сразу при вводе команды. */
    private void sendListedMessage(int count) {
        send(mark()
                .append(part("Предмет ", C_TEXT))
                .append(part(named(count), C_ITEM))
                .append(part(" выставлен на продажу!", C_TEXT)));
    }

    /** «У Вас купили %item% x64 за $%price% на /ah» — через 10 секунд. */
    private void sendSoldMessage(long price, int count) {
        send(mark()
                .append(part("У Вас купили ", C_TEXT))
                .append(part(named(count), C_MARK))
                .append(part(" за ", C_TEXT))
                .append(part("$" + formatPrice(price), C_MONEY))
                .append(part(" на ", C_TEXT))
                .append(part("/ah", C_ITEM)));
    }

    /** «Талисман Крушителя» или «Талисман Крушителя x64» — счётчик тем же цветом, что и название. */
    private static String named(int count) {
        return count > 1 ? ITEM_NAME + " x" + count : ITEM_NAME;
    }

    private static MutableText mark() {
        return Text.empty().append(part("[☃] ", C_MARK));
    }

    private static MutableText part(String text, int rgb) {
        return Text.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(false));
    }

    /** Прямо в чат, без префикса клиента — сообщение должно выглядеть как серверное. */
    private static void send(MutableText text) {
        if (mc.player == null) return;
        mc.player.sendMessage(text, false);
    }

    /** 1000000 → 1,000,000 (как в оригинальных сообщениях аукциона). */
    private static String formatPrice(long price) {
        return group(price, ',');
    }

    // ------------------------------------------------------------------ предмет

    private ItemStack createTalisman(int count) {
        ItemStack stack = new ItemStack(Items.TOTEM_OF_UNDYING, count);
        // У тотема ванильный лимит стака — 1: без этого счётчик и раскладка по слотам ведут себя криво.
        stack.set(DataComponentTypes.MAX_STACK_SIZE, 64);
        stack.set(DataComponentTypes.CUSTOM_NAME, buildName());
        stack.set(DataComponentTypes.LORE, new LoreComponent(buildLore()));
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    /** «&k xxx» по краям + градиент #950000 → #FF0000 → #950000 по буквам, всё жирным. */
    private MutableText buildName() {
        Style bold = Style.EMPTY.withBold(true).withItalic(false);

        MutableText name = Text.empty()
                .append(Text.literal("xxx").setStyle(bold.withObfuscated(true).withColor(TextColor.fromRgb(0x950000))))
                .append(Text.literal(" ").setStyle(bold));

        char[] chars = ITEM_NAME.toCharArray();
        int middle = (chars.length - 1) / 2;
        for (int i = 0; i < chars.length; i++) {
            float t = middle == 0 ? 1f : (float) (middle - Math.abs(i - middle)) / middle;
            int red = 0x95 + Math.round((0xFF - 0x95) * t);
            name.append(Text.literal(String.valueOf(chars[i]))
                    .setStyle(bold.withColor(TextColor.fromRgb(red << 16))));
        }

        return name
                .append(Text.literal(" ").setStyle(bold))
                .append(Text.literal("xxx").setStyle(bold.withObfuscated(true).withColor(TextColor.fromRgb(0x950000))));
    }

    private List<Text> buildLore() {
        Style gray = Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)).withItalic(false);
        Style blue = Style.EMPTY.withColor(TextColor.fromRgb(0x5555FF)).withItalic(false);

        List<Text> lore = new ArrayList<>();
        lore.add(Text.literal("Легендарный символ.").setStyle(gray));
        lore.add(Text.literal("Несокрушимая мощь,").setStyle(gray));
        lore.add(Text.literal("Ломающая преграды.").setStyle(gray));
        lore.add(Text.literal("").setStyle(gray));
        lore.add(Text.literal("Когда во второстепенной руке:").setStyle(gray));
        lore.add(Text.literal("+4 Максимальное здоровье").setStyle(blue));
        lore.add(Text.literal("+3 Урон при атаке").setStyle(blue));
        lore.add(Text.literal("+2 Твёрдость брони").setStyle(blue));
        lore.add(Text.literal("+2 Броня").setStyle(blue));
        return lore;
    }
}
