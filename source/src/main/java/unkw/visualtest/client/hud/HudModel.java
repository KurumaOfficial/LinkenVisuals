package unkw.visualtest.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HudModel {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    // Сглаженный FPS (EMA): сырой getCurrentFps() дёргается каждый кадр, вотермарка мельтешит.
    private static float fpsSmooth;

    // Кэш снапшота: в стиле Card capture() зовут 6 мостов за кадр — данные им нужны одни и те же.
    // TTL 50 мс незаметен глазу, зато срезает 6× сборов/форматирований (и 6× накрутку EMA).
    private static Snapshot cached;
    private static long cacheExpiresAtMs;

    private HudModel() {
    }

    public static Snapshot capture(MinecraftClient client, HudState state) {
        long now = System.currentTimeMillis();
        if (cached != null && now < cacheExpiresAtMs) {
            return cached;
        }
        var player = client.player;
        PlayerInventory inventory = player.getInventory();
        // Тестовые данные (фейк-таргет, прочность, пинг) — только в режиме редактирования HUD:
        // открытый чат ИЛИ редактор Elements (HudEditScreen). Иначе в редакторе элементы без данных
        // невидимы — нечего перетаскивать. В обычной игре всё строго реальное.
        boolean preview = ru.method.linkenvisuals.screen.hud.HudEditScreen.editingActive();
        String coordinates = String.format(Locale.ROOT, "X %d  Y %d  Z %d",
                player.getBlockX(), player.getBlockY(), player.getBlockZ());
        String movement = String.format(Locale.ROOT, "%.1f BPS", state.speedBps());

        PlayerListEntry entry = client.getNetworkHandler() == null
                ? null
                : client.getNetworkHandler().getPlayerListEntry(player.getUuid());
        String ping = entry != null ? entry.getLatency() + " МС" : preview ? "48 МС" : "ЛОКАЛЬНО";

        var activeEffects = player.getStatusEffects();
        state.trackEffects(activeEffects);
        List<EffectRow> effects = collectEffects(activeEffects, preview, state);
        List<CooldownRow> cooldowns = collectCooldowns(client, preview);
        List<ItemStack> armor = collectArmor(player, preview);
        DurabilityAlert alert = collectDurabilityAlert(player, preview);
        Target target = collectTarget(client, preview);

        int rawFps = client.getCurrentFps();
        // EMA-фактор 0.3: смена резкая и плавная, но КОРОТКАЯ — цифра догоняет новое значение за
        // ~8-10 кадров без дёрганья (раньше 0.02 давал долгую вялую «доводку»).
        fpsSmooth = fpsSmooth <= 0.0F ? rawFps : fpsSmooth + (rawFps - fpsSmooth) * 0.3F;

        cached = new Snapshot(
                Math.round(fpsSmooth),
                player.getName().getString(),
                ping,
                coordinates,
                movement,
                effects,
                cooldowns,
                armor,
                alert,
                target,
                preview
        );
        cacheExpiresAtMs = now + 50L;
        return cached;
    }

    private static List<EffectRow> collectEffects(Iterable<StatusEffectInstance> active, boolean preview, HudState state) {
        List<EffectRow> rows = new ArrayList<>();
        for (StatusEffectInstance effect : active) {
            int seconds = effect.isInfinite() ? Integer.MAX_VALUE : Math.max(0, effect.getDuration() / 20);
            RegistryEntry<StatusEffect> type = effect.getEffectType();
            rows.add(new EffectRow(
                    type,
                    Text.translatable(effect.getTranslationKey()).getString(),
                    effect.getAmplifier() + 1,
                    seconds == Integer.MAX_VALUE ? "∞" : formatDuration(seconds),
                    seconds,
                    type.value().getCategory() == StatusEffectCategory.HARMFUL
            ));
        }

        rows.sort(Comparator.comparingLong((EffectRow row) -> state.effectOrder(row.type())).reversed());
        // В игре — только реальные. Превью-строки лишь в режиме редактирования (чат/Elements),
        // иначе пустое окно не рисуется и его нечего перетаскивать.
        if (rows.isEmpty() && preview) {
            int phase = (int) ((System.currentTimeMillis() / 1000L) % 20L);
            rows.add(new EffectRow(StatusEffects.SPEED, "Скорость", 2, formatDuration(84 - phase), 84 - phase, false));
            rows.add(new EffectRow(StatusEffects.RESISTANCE, "Сопротивление", 1, formatDuration(38 - phase / 2), 38 - phase / 2, false));
        }
        return rows;
    }

    private static List<CooldownRow> collectCooldowns(MinecraftClient client, boolean preview) {
        // Готовые кулдауны с остатком времени берём из мод-утилиты (CooldownsHud её же использует).
        // Значение — секунды вместо процентов; в игре только реальные, превью-строки — лишь в редакторе.
        List<CooldownRow> rows = new ArrayList<>();
        for (ru.method.linkenvisuals.utils.inv.CooldownUtils.CooldownEntry entry
                : ru.method.linkenvisuals.utils.inv.CooldownUtils.collectActive(client, 0.0F)) {
            int seconds = Math.max(0, Math.round(entry.remainingTicks() / 20.0F));
            rows.add(new CooldownRow(entry.stack(), entry.displayName(), formatDuration(seconds)));
            if (rows.size() == 3) {
                break;
            }
        }
        if (rows.isEmpty() && preview) {
            rows.add(new CooldownRow(new ItemStack(Items.ENDER_PEARL), "Эндер-жемчуг", formatDuration(12)));
            rows.add(new CooldownRow(new ItemStack(Items.CHORUS_FRUIT), "Плод хоруса", formatDuration(3)));
        }
        return rows;
    }

    private static List<ItemStack> collectArmor(LivingEntity player, boolean preview) {
        // Только реальная броня — тестовые алмазные сэмплы убраны по запросу.
        List<ItemStack> slots = new ArrayList<>(ARMOR_SLOTS.length);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            slots.add(player.getEquippedStack(slot));
        }
        return slots;
    }

    private static DurabilityAlert collectDurabilityAlert(LivingEntity player, boolean preview) {
        ItemStack lowest = ItemStack.EMPTY;
        int lowestRemaining = Integer.MAX_VALUE;
        int lowestPercent = 100;
        List<ItemStack> candidates = new ArrayList<>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            candidates.add(player.getEquippedStack(slot));
        }
        candidates.add(player.getMainHandStack());
        candidates.add(player.getOffHandStack());

        for (ItemStack stack : candidates) {
            if (stack.isEmpty() || !stack.isDamageable() || stack.getMaxDamage() <= 0) {
                continue;
            }
            int remaining = stack.getMaxDamage() - stack.getDamage();
            int percent = Math.round(remaining * 100.0F / stack.getMaxDamage());
            if (percent > 15 && remaining > 30) {
                continue;
            }
            if (percent < lowestPercent || percent == lowestPercent && remaining < lowestRemaining) {
                lowest = stack;
                lowestRemaining = remaining;
                lowestPercent = percent;
            }
        }

        if (!lowest.isEmpty()) {
            return new DurabilityAlert(lowest, lowestPercent);
        }
        if (preview) {
            ItemStack sample = new ItemStack(Items.DIAMOND_PICKAXE);
            sample.setDamage(Math.max(0, sample.getMaxDamage() - 74));
            return new DurabilityAlert(sample, Math.round(74.0F * 100.0F / sample.getMaxDamage()));
        }
        return null;
    }

    // Насколько «мимо» игрока можно смотреть и всё равно поймать таргет (блоки вокруг хитбокса).
    private static final double AIM_HITBOX_MARGIN = 0.75;
    // Только БЛИЗКИЕ игроки: таргет ловится, когда игрок рядом, а не за десятки блоков.
    private static final double AIM_MAX_RANGE = 3.5;

    private static Target collectTarget(MinecraftClient client, boolean preview) {
        // 1) Игрок рядом с прицелом (расширенный хитбокс): таргет ловится, даже если смотришь чуть
        //    левее/правее/ниже игрока, а не строго в центр. 2) Фолбэк — прямой ванильный crosshair-таргет
        //    (мобы и т.п.). Невидимых не показываем — как основной TargetHUD.
        LivingEntity target = playerNearCrosshair(client);
        if (target == null && client.targetedEntity instanceof LivingEntity living && living != client.player
                && !living.isInvisible()
                && !living.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.INVISIBILITY)) {
            target = living;
        }
        if (target != null) {
            return new Target(target, target.getName().getString(),
                    target.getHealth(), target.getMaxHealth(), client.player.distanceTo(target));
        }
        return preview ? new Target(client.player, client.player.getName().getString(), 13.5F, 20.0F, 4.2F) : null;
    }

    /** Ближайший к лучу прицела игрок, чей РАСШИРЕННЫЙ хитбокс встречает взгляд (не строго по центру). */
    private static LivingEntity playerNearCrosshair(MinecraftClient client) {
        if (client.world == null || client.player == null) return null;
        net.minecraft.util.math.Vec3d eye = client.player.getEyePos();
        net.minecraft.util.math.Vec3d look = client.player.getRotationVec(1.0f);
        net.minecraft.util.math.Vec3d rayEnd = eye.add(look.multiply(AIM_MAX_RANGE));
        net.minecraft.entity.player.PlayerEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (net.minecraft.entity.player.PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player) continue;
            if (p.isInvisible() || p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.INVISIBILITY)) continue;
            if (client.player.distanceTo(p) > AIM_MAX_RANGE) continue;
            var hit = p.getBoundingBox().expand(AIM_HITBOX_MARGIN).raycast(eye, rayEnd);
            if (hit.isEmpty()) continue;
            double d = hit.get().squaredDistanceTo(eye);
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    private static String formatDuration(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static ItemStack damaged(Item item, float damageRatio) {
        ItemStack stack = new ItemStack(item);
        if (stack.isDamageable()) {
            stack.setDamage(Math.round(stack.getMaxDamage() * clamp(damageRatio)));
        }
        return stack;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public record Snapshot(
            int fps,
            String playerName,
            String ping,
            String coordinates,
            String movement,
            List<EffectRow> effects,
            List<CooldownRow> cooldowns,
            List<ItemStack> armor,
            DurabilityAlert alert,
            Target target,
            boolean preview
    ) {
    }

    public record EffectRow(RegistryEntry<StatusEffect> type, String name, int amplifier, String duration, int seconds,
                            boolean harmful) {
    }

    public record CooldownRow(ItemStack stack, String name, String value) {
    }

    public record DurabilityAlert(ItemStack stack, int percent) {
    }

    public record Target(LivingEntity entity, String name, float health, float maxHealth, float distance) {
    }
}
