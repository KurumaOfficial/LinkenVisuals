package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.modules.impl.client.UI;
import ru.method.linkenvisuals.utils.animations.Animation;
import ru.method.linkenvisuals.utils.animations.Easing;
import ru.method.linkenvisuals.utils.debug.TabProfiler;
import ru.method.linkenvisuals.utils.render.HudRenderFrame;
import ru.method.linkenvisuals.utils.render.Render2D;
import ru.method.linkenvisuals.utils.text.StreamerModeUtils;
import ru.method.linkenvisuals.utils.text.SyncedUserTextUtils;

import java.awt.Color;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    // --- Кастом-таб: своя фон-панель (скругление + блюр + цвет + анимация). Начинка ванильная. ---
    // Границы фона прошлого кадра (панель рисуем по ним на HEAD — до начинки; лаг 1 кадр незаметен).
    @Unique private static float lvTabMinX, lvTabMinY, lvTabMaxX, lvTabMaxY;
    @Unique private static boolean lvTabHasBounds;
    // Сбор границ текущего кадра (из перехваченных fill).
    @Unique private static float lvCapMinX, lvCapMinY, lvCapMaxX, lvCapMaxY;
    @Unique private static boolean lvCapHas;
    @Unique private static long lvTabLastMs;
    @Unique private static final Animation lvTabOpen = new Animation(280, 1f, false, Easing.EASE_OUT_CUBIC);
    // Матрица открытия: масштаб+слайд применяются ко ВСЕМУ табу (панель и ванильная начинка вместе).
    @Unique private static boolean lvTabMatrixPushed;
    @Inject(method = "render", at = @At("HEAD"))
    private void dv$renderBeforePlayerList(DrawContext context, int scaledWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        TabProfiler.beginFrame();
        HudRenderFrame.tryDispatchBeforeOverlays(LinkenVisuals.getInstance().getEventHandler());
        if (!lvRenderLogged) {
            lvRenderLogged = true;
            int listed = -1;
            try {
                var mc = net.minecraft.client.MinecraftClient.getInstance();
                if (mc != null && mc.getNetworkHandler() != null) {
                    listed = mc.getNetworkHandler().getListedPlayerListEntries().size();
                }
            } catch (Throwable ignored) {
            }
            LinkenVisuals.LOGGER.info("[TabBadge] tab render alive, listed entries={}", listed);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void lvCustomTabBackground(DrawContext context, int scaledWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        long now = System.currentTimeMillis();
        if (now - lvTabLastMs > 200L) {
            lvTabOpen.snapTo(false); // таб переоткрыли — анимация с нуля
        }
        lvTabLastMs = now;
        lvTabOpen.update(true);
        float open = Math.max(0f, Math.min(1f, lvTabOpen.getValue()));
        lvCapHas = false; // начинаем собирать границы этого кадра заново

        // Плавное открытие ВСЕГО таба (и ванильного, и кастомного): выезд из-за верхнего края вниз.
        // Смещение стартует за экраном (низ таба прошлого кадра + запас) и приходит в 0.
        var ms = context.getMatrices();
        ms.pushMatrix();
        lvTabMatrixPushed = true;
        float slideFrom = lvTabHasBounds ? (lvTabMaxY + 8f) : 90f;
        ms.translate(0f, -slideFrom * (1f - open));

        if (!UI.isCustomTabEnabled()) return;

        if (lvTabHasBounds) {
            float pad = 2f;
            float bx1 = lvTabMinX - pad, by1 = lvTabMinY - pad, bx2 = lvTabMaxX + pad, by2 = lvTabMaxY + pad;
            float radius = 6f;
            Color col = UI.getCustomTabColor();
            int a = Math.max(0, Math.min(255, Math.round(col.getAlpha() * open)));
            Render2D.drawBlurredRect(context.getMatrices(), bx1, by1, bx2 - bx1, by2 - by1, radius, 12f,
                    new Color(col.getRed(), col.getGreen(), col.getBlue(), Math.round(a * 0.6f)));
            Render2D.drawRoundedRect(context.getMatrices(), bx1, by1, bx2 - bx1, by2 - by1, radius,
                    new Color(col.getRed(), col.getGreen(), col.getBlue(), a));
        }
    }

    // Перехватываем фоновые прямоугольники ванильного таба: собираем их общие границы (нужны и для
    // слайд-анимации, и для кастом-панели). При кастом-табе подавляем отрисовку — фон заменён
    // одной скруглённой+блюр+цветной панелью (см. lvCustomTabBackground).
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"), require = 0)
    private void lvCaptureTabFill(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        int minx = Math.min(x1, x2), miny = Math.min(y1, y2), maxx = Math.max(x1, x2), maxy = Math.max(y1, y2);
        if (!lvCapHas) {
            lvCapMinX = minx; lvCapMinY = miny; lvCapMaxX = maxx; lvCapMaxY = maxy; lvCapHas = true;
        } else {
            lvCapMinX = Math.min(lvCapMinX, minx); lvCapMinY = Math.min(lvCapMinY, miny);
            lvCapMaxX = Math.max(lvCapMaxX, maxx); lvCapMaxY = Math.max(lvCapMaxY, maxy);
        }
        if (!UI.isCustomTabEnabled()) {
            ctx.fill(x1, y1, x2, y2, color); // ванильный фон рисуем как есть
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void lvCommitTabBounds(DrawContext context, int scaledWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        // Снимаем матрицу открытия независимо от прочих условий — push всегда парный pop.
        if (lvTabMatrixPushed) {
            context.getMatrices().popMatrix();
            lvTabMatrixPushed = false;
        }
        if (lvCapHas) {
            lvTabMinX = lvCapMinX; lvTabMinY = lvCapMinY; lvTabMaxX = lvCapMaxX; lvTabMaxY = lvCapMaxY;
            lvTabHasBounds = true;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void linkenvisuals$finishTabProfile(DrawContext context, int scaledWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        TabProfiler.endFrame();
    }

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void linkenvisuals$sanitizeTabName(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        try {
            if (!lvRowsPathLogged) {
                lvRowsPathLogged = true;
                LinkenVisuals.LOGGER.info("[TabBadge] rows path alive (getPlayerName вызывается)");
            }
            String pname = entry == null || entry.getProfile() == null ? null : entry.getProfile().name();
            java.util.UUID pid = entry == null || entry.getProfile() == null ? null : entry.getProfile().id();
            Text sanitized = StreamerModeUtils.sanitizeTabName(cir.getReturnValue(), pname);
            cir.setReturnValue(SyncedUserTextUtils.applyBadgeForPlayerSanitized(sanitized, pid, pname));
        } catch (Throwable t) {
            // Бейдж никогда не должен ронять таб — при ошибке остаётся ванильное имя.
        }
    }

    // ВРЕМЕННАЯ ДИАГНОСТИКА вставки бейджей в таб (throttle 5 c). Убрать после проверки.
    @Unique private static long lvTabBadgeLogMs;
    @Unique private static boolean lvRowsPathLogged;
    @Unique private static boolean lvRenderLogged;
    @Unique private static int lvDrawSampleCount;

    @Unique
    private static void lvSampleDraw(String kind, String plain) {
        if (lvDrawSampleCount >= 6 || plain == null) return;
        String trimmed = plain.replace('\n', ' ').trim();
        if (trimmed.isEmpty()) return;
        lvDrawSampleCount++;
        LinkenVisuals.LOGGER.info("[TabBadge] draw[{}] '{}'", kind,
                trimmed.substring(0, Math.min(90, trimmed.length())));
    }

    // FunTime-подобные сервера рисуют ВЕСЬ таб хедером/футером (строк игроков нет вовсе —
    // getPlayerName ни разу не вызывается), поэтому значки синхронизированных юзеров вставляем
    // прямо в текст хедера. applyBadgeDeepForTab = санитайз + бейдж у каждого synced-ника И у
    // своего ника; вызывается только на смене хедера (пакет), не каждый кадр.
    @ModifyVariable(method = "setHeader", at = @At("HEAD"), argsOnly = true, index = 1)
    private Text linkenvisuals$sanitizeTabHeader(Text header) {
        StreamerModeUtils.observeTabText(header);
        try {
            Text result = SyncedUserTextUtils.applyBadgeDeepForTab(header);
            long now = System.currentTimeMillis();
            if (now - lvTabBadgeLogMs > 5000L) {
                lvTabBadgeLogMs = now;
                LinkenVisuals.LOGGER.info("[TabBadge] header {}", SyncedUserTextUtils.tabBadgeDebug(header, result));
            }
            return result;
        } catch (Throwable t) {
            LinkenVisuals.LOGGER.warn("[TabBadge] header insert threw", t);
            return StreamerModeUtils.sanitizeText(header);
        }
    }

    @ModifyVariable(method = "setFooter", at = @At("HEAD"), argsOnly = true, index = 1)
    private Text linkenvisuals$sanitizeTabFooter(Text footer) {
        StreamerModeUtils.observeTabText(footer);
        try {
            return SyncedUserTextUtils.applyBadgeDeepForTab(footer);
        } catch (Throwable t) {
            return StreamerModeUtils.sanitizeText(footer);
        }
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"
            ),
            index = 1
    )
    private Text linkenvisuals$sanitizeTabText(Text text) {
        try {
            // getString() на каждую строку каждый кадр — только пока сэмплы ещё собираются.
            if (lvDrawSampleCount < 6 && text != null) lvSampleDraw("Text", text.getString());
            return lvProcessRowText(text);
        } catch (Throwable t) {
            return text;
        }
    }

    // Кэш обработанных строк таба: обработка идёт на КАЖДОМ кадре для каждой строки, а результат
    // детерминирован по plain-тексту. TTL 2 c, чтобы подхватывать обновления списка синка/стримера.
    @Unique private static final java.util.Map<String, Text> lvRowCache = new java.util.concurrent.ConcurrentHashMap<>();
    @Unique private static long lvRowCacheClearMs;

    /**
     * Санитайз (донат стример-мода) + значок визуалов для СТРОКИ таба прямо на отрисовке.
     * Figura перехватывает вызов getPlayerName внутри render (method_1918 → её getNameForDisplay),
     * поэтому наш инжект в getPlayerName не вызывается вовсе — единственная надёжная точка, через
     * которую реально проходят ники строк, это drawTextWithShadow.
     */
    @Unique
    private static Text lvProcessRowText(Text text) {
        if (text == null) return null;
        String plain = text.getString();
        if (plain == null || plain.isBlank()) return text;
        long now = System.currentTimeMillis();
        if (now - lvRowCacheClearMs > 2000L) {
            lvRowCache.clear();
            lvRowCacheClearMs = now;
        }
        Text cached = lvRowCache.get(plain);
        if (cached != null) return cached;

        net.minecraft.client.network.PlayerListEntry match = null;
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc != null && mc.getNetworkHandler() != null) {
            int bestLen = 0;
            for (net.minecraft.client.network.PlayerListEntry e : mc.getNetworkHandler().getListedPlayerListEntries()) {
                if (e == null || e.getProfile() == null) continue;
                String pn = e.getProfile().name();
                if (pn == null || pn.length() < 3 || pn.length() <= bestLen) continue;
                if (plain.contains(pn)) {
                    match = e;
                    bestLen = pn.length();
                }
            }
        }
        Text result;
        if (match != null) {
            String pname = match.getProfile().name();
            Text sanitized = StreamerModeUtils.sanitizeTabName(text, pname);
            result = SyncedUserTextUtils.applyBadgeForPlayerSanitized(sanitized, match.getProfile().id(), pname);
        } else {
            result = SyncedUserTextUtils.moveSyncedBadgeBeforePlayerDonate(text);
        }
        if (result == null) result = text;
        lvDumpRowStyles(text, result);
        if (lvRowCache.size() < 256) lvRowCache.put(plain, result);
        return result;
    }

    // ВРЕМЕННАЯ ДИАГНОСТИКА: дамп цветовыхранов итоговой строки таба (только изменённые строки,
    // первые 4 за запуск) — видно, доехал ли цвет &-кодов кастом-доната до финального текста.
    @Unique private static int lvStyleDumpCount;

    @Unique
    private static void lvDumpRowStyles(Text original, Text result) {
        if (lvStyleDumpCount >= 4 || result == original) return;
        lvStyleDumpCount++;
        StringBuilder sb = new StringBuilder();
        final net.minecraft.text.TextColor[] lastCol = {null};
        final boolean[] first = {true};
        result.asOrderedText().accept((idx, style, cp) -> {
            net.minecraft.text.TextColor c = style == null ? null : style.getColor();
            if (first[0] || !java.util.Objects.equals(c, lastCol[0])) {
                sb.append('[').append(c == null ? "def" : c.toString()).append(']');
                lastCol[0] = c;
                first[0] = false;
            }
            sb.appendCodePoint(cp);
            return sb.length() < 220;
        });
        LinkenVisuals.LOGGER.info("[TabBadge] rowStyled {}", sb);
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V"
            ),
            index = 1,
            require = 0
    )
    private OrderedText linkenvisuals$sanitizeTabOrderedText(OrderedText text) {
        try {
            // lvPlainOf аллоцирует StringBuilder на строку за кадр — только пока сэмплы собираются.
            if (lvDrawSampleCount < 6 && text != null) lvSampleDraw("Ordered", lvPlainOf(text));
            return SyncedUserTextUtils.moveSyncedBadgeBeforePlayerDonate(text);
        } catch (Throwable t) {
            return text;
        }
    }

    @Unique
    private static String lvPlainOf(OrderedText text) {
        StringBuilder sb = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return sb.length() < 120;
        });
        return sb.toString();
    }
}
