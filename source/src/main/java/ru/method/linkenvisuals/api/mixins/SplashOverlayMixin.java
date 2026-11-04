package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Overlay;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.screen.overlay.LinkenLoadingScreen;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Подменяет красный загрузочный экран Mojang на экран 9d клиента.
 *
 * <p>Ванильный {@code render} отменяется целиком: логотип Mojang рисуется слоем
 * {@code RenderLayer.getMojangLogo()} с чисто аддитивным блендингом, поэтому «закрасить» его сверху
 * нельзя — он проступает сквозь любой фон. Вместе с отменой здесь воспроизводится вся служебная
 * логика оверлея (тайминги, прогресс, завершение релоада, снятие оверлея) — без неё игра зависает
 * на загрузке.
 *
 * <p>Затухание сокращено против ванильных двух секунд до ~0.4 с: ванильная пауза нужна была под
 * анимацию логотипа Mojang, а держать после этого неактивный оверлей поверх готового меню незачем.
 *
 * <p>Если наш экран по любой причине падает — миксин помечает себя сломанным и перестаёт отменять
 * ванильный рендер, то есть худший исход это возврат к экрану Mojang, а не чёрный экран.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin extends Overlay {

    @Unique
    private static final Logger linkenvisuals$LOGGER = LoggerFactory.getLogger("LinkenVisuals/Splash");

    /** Пауза после завершения релоада перед уходом экрана, в секундах. */
    @Unique
    private static final float linkenvisuals$HOLD = 0.12f;
    /** Длительность ухода экрана вверх, в секундах. */
    @Unique
    private static final float linkenvisuals$SLIDE = 0.45f;

    @Unique
    private static boolean linkenvisuals$broken;
    @Unique
    private long linkenvisuals$lastFrameMs;

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private ResourceReload reload;
    @Shadow @Final private Consumer<Optional<Throwable>> exceptionHandler;
    @Shadow @Final private boolean reloading;
    @Shadow private float progress;
    @Shadow private long reloadCompleteTime;
    @Shadow private long reloadStartTime;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$customSplash(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (linkenvisuals$broken) return;

        try {
            long now = Util.getMeasuringTimeMs();
            if (this.reloading && this.reloadStartTime == -1L) this.reloadStartTime = now;

            float sinceComplete = this.reloadCompleteTime > -1L
                    ? (float) (now - this.reloadCompleteTime) / 1000f : -1f;
            float sinceStart = this.reloadStartTime > -1L
                    ? (float) (now - this.reloadStartTime) / 500f : -1f;

            // Сглаживание прогресса по ВРЕМЕНИ, а не по кадрам. Ванильное progress*0.95+p*0.05 зависит
            // от частоты кадров, а во время загрузки ресурсов кадры редкие и неравномерные — бар дёргался
            // рывками. Экспонента с dt даёт одинаковую плавность при любом FPS.
            // Скорость догона 2.2/с: реальный прогресс релоада прыгает крупными шагами, и быстрый догон
            // (был 4.5/с) повторял эти скачки почти без сглаживания — заполнение выглядело резким.
            // Кап dt 0.1с: одиночный длинный кадр не должен телепортировать бар. После завершения
            // релоада догоняем быстро (10/с), чтобы бар дошёл до 100% за паузу перед уходом экрана.
            float dt = linkenvisuals$lastFrameMs == 0L ? 0.016f
                    : Math.min(0.1f, (now - linkenvisuals$lastFrameMs) / 1000f);
            linkenvisuals$lastFrameMs = now;
            float rate = this.reloadCompleteTime > -1L || this.reload.isComplete() ? 10f : 2.2f;
            float k = 1f - (float) Math.exp(-rate * dt);
            this.progress = MathHelper.clamp(
                    this.progress + (this.reload.getProgress() - this.progress) * k, 0f, 1f);

            // Служебная часть ванили — строго до отрисовки, чтобы сбой рендера не подвесил загрузку.
            if (this.reloadCompleteTime == -1L && this.reload.isComplete()
                    && (!this.reloading || sinceStart >= 2f)) {
                try {
                    this.reload.throwException();
                    this.exceptionHandler.accept(Optional.empty());
                } catch (Throwable t) {
                    this.exceptionHandler.accept(Optional.of(t));
                }
                this.reloadCompleteTime = Util.getMeasuringTimeMs();
                ru.method.linkenvisuals.utils.client.MainMenuGreeting.onReloadComplete();
                if (this.client.currentScreen != null) {
                    this.client.currentScreen.init(context.getScaledWindowWidth(),
                            context.getScaledWindowHeight());
                }
                sinceComplete = 0f;
            }

            float alpha = 1f;
            float exit = 0f;
            if (sinceComplete >= 0f) {
                exit = MathHelper.clamp(
                        (sinceComplete - linkenvisuals$HOLD) / linkenvisuals$SLIDE, 0f, 1f);
                // Главное меню рисуем ПОД уезжающим экраном — иначе за его нижней кромкой пустота.
                if (this.client.currentScreen != null) {
                    this.client.currentScreen.render(context, mouseX, mouseY, delta);
                }
            } else if (this.reloading) {
                // Перезагрузка ресурсов уже в игре (F3+T, смена ресурспаков) — проявляемся плавно.
                alpha = MathHelper.clamp(sinceStart, 0f, 1f);
                if (this.client.currentScreen != null && sinceStart < 1f) {
                    this.client.currentScreen.render(context, mouseX, mouseY, delta);
                }
            }

            LinkenLoadingScreen.render(context, this.progress, alpha, exit);

            if (exit >= 1f) {
                LinkenLoadingScreen.dispose();
                this.client.setOverlay(null);
            }

            ci.cancel();
        } catch (Throwable t) {
            // Не отменяем кадр: пусть дальше рисует ваниль. Чёрного экрана быть не должно.
            linkenvisuals$broken = true;
            linkenvisuals$LOGGER.error("[splash] кастомный загрузочный экран отключён после сбоя", t);
            try {
                LinkenLoadingScreen.dispose();
            } catch (Throwable ignored) {
            }
        }
    }
}
