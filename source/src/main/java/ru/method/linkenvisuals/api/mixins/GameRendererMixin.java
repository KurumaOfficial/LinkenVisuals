package ru.method.linkenvisuals.api.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.api.events.impl.EventRenderWorld;
import ru.method.linkenvisuals.api.events.impl.rotations.EventTrace;
import ru.method.linkenvisuals.modules.impl.render.AspectRatio;
import ru.method.linkenvisuals.modules.impl.render.NoRender;
import ru.method.linkenvisuals.modules.impl.render.Zoom;
import ru.method.linkenvisuals.utils.Wrapper;
import dev.linkenvisuals.hands.HandEditor;
import dev.linkenvisuals.hands.HandOutline;
import ru.method.linkenvisuals.utils.render.HandsRenderer;
import ru.method.linkenvisuals.utils.render.Render3D;
import ru.method.linkenvisuals.utils.render.ClickGuiRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.objectweb.asm.Opcodes;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin implements Wrapper {

    @Shadow public abstract float getFarPlaneDistance();
    @Shadow protected abstract void renderHand(float tickDelta, boolean sleeping, Matrix4f matrix4f);
    @Shadow public abstract Camera getCamera();
    @Shadow private float getFov(Camera camera, float tickProgress, boolean changingFov) {
        throw new AssertionError();
    }

    @Unique
    private boolean linkenvisuals$fogRendered;

    @Inject(method = "renderWorld", at = @At("HEAD"), cancellable = true)
    public void renderWorld(RenderTickCounter renderTickCounter, CallbackInfo ci) {
        linkenvisuals$fogRendered = false;
        HandsRenderer.beginFrame();
        // Фирменный анимированный плащ: рисуем его FBO ДО ресинка юнита-0 ниже.
        ru.method.linkenvisuals.cosmetics.linken.LinkenCape.renderFrame();
        // Стерильный старт кадра мира — форс-ресинк текстур-юнита 0. HUD ПРОШЛОГО кадра (ватермарка
        // round.png / MSDF-глифы через BuiltText/RoundFontRenderer) оставляет свой атлас ФИЗИЧЕСКИ на
        // юните 0, при этом кэш GlStateManager и managed-слот RenderSystem рассинхронены (их
        // setShaderTexture(0,0) трогает только managed-массив). Из-за этого ванильный рендер энд-неба
        // (сэмплит юнит 0 «как есть») красился глифами атласа, а карты в рамках — тем более.
        // Простой bindTexture(0) не помогал: (1) не выставлял активный юнит 0 — чистился не тот юнит;
        // (2) _bindTexture cache-guarded — пропускает бинд, если кэш уже читает 0 при рассинхроне.
        // Лечим: активный юнит 0 → бинд ЗАВЕДОМО живого ненулевого id (color-attachment главного FBO) →
        // бинд 0. Первый бинд гарантирует, что второй не схлопнется guard'ом → кэш==физика==0, и все
        // последующие ванильные managed-бинды (небо/террейн/сущности/карты) ложатся верно.
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        boolean linkGuiOpen = client.currentScreen
                instanceof ru.method.linkenvisuals.screen.linkgui.LinkGuiScreen;
        if (linkGuiOpen) {
            ru.method.linkenvisuals.modules.impl.client.BoostFpsMode mode =
                    ru.method.linkenvisuals.modules.impl.client.UI.getBoostFpsMode();
            if (mode == ru.method.linkenvisuals.modules.impl.client.BoostFpsMode.MIDDLE) {
                // MIDDLE: вместо живого рендера мира показываем замороженный размытый снимок —
                // мир виден как фон, но кадр почти бесплатный (камера в открытом GUI не двигается).
                if (ru.method.linkenvisuals.utils.render.WorldSnapshotCache.isValid()) {
                    ru.method.linkenvisuals.utils.render.WorldSnapshotCache.blitTo(client.getFramebuffer());
                    ci.cancel();
                    return;
                }
                // Снимка ещё нет (первый кадр после открытия) — рендерим мир и захватим его в TAIL.
            } else {
                // DEFAULT: ничего не меняем — мир рендерится как обычно.
                ru.method.linkenvisuals.utils.render.WorldSnapshotCache.invalidate();
            }
        } else {
            ru.method.linkenvisuals.utils.render.WorldSnapshotCache.invalidate();
        }
        Render3D.prepare();
    }


    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void linkenvisuals$captureWorldSnapshot(RenderTickCounter renderTickCounter, CallbackInfo ci) {
        // Захватываем отрендеренный мир в снимок один раз при открытии GUI в режиме MIDDLE. TAIL не
        // вызывается, когда HEAD отменил проход (снимок уже есть, либо HIGH) — захват происходит ровно раз.
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (ru.method.linkenvisuals.modules.impl.client.UI.getBoostFpsMode()
                        == ru.method.linkenvisuals.modules.impl.client.BoostFpsMode.MIDDLE
                && client.currentScreen instanceof ru.method.linkenvisuals.screen.linkgui.LinkGuiScreen
                && !ru.method.linkenvisuals.utils.render.WorldSnapshotCache.isValid()) {
            ru.method.linkenvisuals.utils.render.WorldSnapshotCache.capture(client.getFramebuffer());
        }
    }


    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
    public void showFloatingItem(ItemStack floatingItem, CallbackInfo ci) {
        NoRender noRenderModule = LinkenVisuals.getInstance().getModuleManager().getModule(NoRender.class);
        if (noRenderModule != null && noRenderModule.isToggled() && noRenderModule.totem.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    public void tiltViewWhenHurt(CallbackInfo ci) {
        NoRender noRenderModule = LinkenVisuals.getInstance().getModuleManager().getModule(NoRender.class);
        if (noRenderModule != null && noRenderModule.isToggled() && noRenderModule.hurtCam.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void linkenvisuals$getFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        Zoom zoom = LinkenVisuals.getInstance().getModuleManager().getModule(Zoom.class);
        if (zoom == null || !zoom.isToggled()) {
            return;
        }

        float multiplier = zoom.getFovMultiplier(tickDelta);
        if (multiplier == 1.0f) {
            return;
        }

        cir.setReturnValue(cir.getReturnValueF() * multiplier);
    }

    @Inject(method = "getNightVisionStrength", at = @At("HEAD"), cancellable = true)
    private static void linkenvisuals$fullbrightNightVision(net.minecraft.entity.LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && entity == client.player) {
            boolean active = false;
            if (LinkenVisuals.getInstance() != null && LinkenVisuals.getInstance().getModuleManager() != null) {
                ru.method.linkenvisuals.modules.impl.render.Fullbright fb =
                        LinkenVisuals.getInstance().getModuleManager().getModule(ru.method.linkenvisuals.modules.impl.render.Fullbright.class);
                if (fb != null && fb.isToggled()) active = true;
            }
            if (!active) {
                dev.linkenvisuals.module.impl.FullbrightModule devMod =
                        dev.linkenvisuals.module.Modules.fullbright();
                if (devMod != null && devMod.enabled()) active = true;
            }
            if (active) {
                cir.setReturnValue(1.0f);
            }
        }
    }

    @Inject(method = "getBasicProjectionMatrix", at = @At("TAIL"), cancellable = true)
    public void linkenvisuals$aspectRatio(float fov, CallbackInfoReturnable<Matrix4f> cir) {
        AspectRatio aspectRatio = LinkenVisuals.getInstance() == null || LinkenVisuals.getInstance().getModuleManager() == null
                ? null : LinkenVisuals.getInstance().getModuleManager().getModule(AspectRatio.class);
        if (aspectRatio != null && aspectRatio.isToggled()) {
            cir.setReturnValue(dev.linkenvisuals.render.ProjectionMath.withAspect(cir.getReturnValue(), aspectRatio.ratio.getValue()));
        }
    }

    /**
     * FogBlur must sample the world depth buffer, but 1.21.11 clears it right
     * before {@code renderHand}. Run the effect immediately before that clear
     * (after the world, particles and weather are complete).
     */
    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"
            ),
            require = 0
    )
    private void linkenvisuals$fogBeforeHandDepthClear(RenderTickCounter renderTickCounter, CallbackInfo ci) {
        linkenvisuals$renderFogBlur();
        // Snapshot the final world (including fog) before the hand pass; the
        // Hands mask compares this with the post-hand frame.
        HandsRenderer.captureBeforeHand();
    }

    @Unique
    private void linkenvisuals$renderFogBlur() {
        if (linkenvisuals$fogRendered) {
            return;
        }
        ru.method.linkenvisuals.modules.impl.render.FogBlur fogBlur =
                LinkenVisuals.getInstance() != null && LinkenVisuals.getInstance().getModuleManager() != null
                        ? LinkenVisuals.getInstance().getModuleManager().getModule(ru.method.linkenvisuals.modules.impl.render.FogBlur.class)
                        : null;
        if (fogBlur != null && fogBlur.isToggled() && mc.player != null
                && !mc.gameRenderer.isRenderingPanorama()) {
            fogBlur.renderCurrent();
            linkenvisuals$fogRendered = true;
        }
    }

    @Redirect(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;renderHand(FZLorg/joml/Matrix4f;)V"
            )
    )
    private void linkenvisuals$renderShaderHands(GameRenderer instance, float tickDelta, boolean sleeping, Matrix4f matrix4f) {
        // Fallback for mappings where the pre-clear injection is unavailable.
        linkenvisuals$renderFogBlur();
        HandsRenderer.captureBeforeHand();

        // Run motion blur on the world before the hand is rendered, so the hand stays 100% solid.
        ru.method.linkenvisuals.modules.impl.render.MotionBlur motionBlur =
                LinkenVisuals.getInstance() != null && LinkenVisuals.getInstance().getModuleManager() != null
                        ? LinkenVisuals.getInstance().getModuleManager().getModule(ru.method.linkenvisuals.modules.impl.render.MotionBlur.class)
                        : null;
        if (motionBlur != null && motionBlur.isToggled()) {
            motionBlur.renderCurrent();
        }

        this.renderHand(tickDelta, sleeping, matrix4f);
    }

    @Inject(
            method = "renderHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;draw()V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void linkenvisuals$captureHandlessFrame(float tickDelta, boolean sleeping,
                                                    Matrix4f matrix4f, CallbackInfo ci) {
        // renderHand first flushes the world entity queue, then queues the
        // first-person hand. This is the only moment where the frame contains
        // all world entities but not the hand yet.
        HandsRenderer.captureBeforeHand();
        // Same anchor for the Strange outline: capturing at renderHand HEAD
        // included world entities drawn during renderHand in the depth delta,
        // which tinted large parts of the frame.
        HandOutline.captureBefore();
    }

    /**
     * Hands must be applied after the deferred hand command queue and the
     * screen overlays have actually been drawn. Doing it in the renderHand
     * redirect let the vanilla hand draw land on top of the effect, so the
     * selected mode looked like the normal skin.
     */
    @Inject(method = "renderHand", at = @At("HEAD"))
    private void linkenvisuals$captureHandOutline(float tickDelta, boolean sleeping,
                                                  Matrix4f matrix4f, CallbackInfo ci) {
        if (HandEditor.active() && getCamera() != null) {
            HandEditor.setFov(getFov(getCamera(), tickDelta, true));
            HandEditor.update();
        }
    }

    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void linkenvisuals$handsAfterWorld(RenderTickCounter renderTickCounter, CallbackInfo ci) {
        if (HandsRenderer.isActive()) {
            HandsRenderer.render(this.getFarPlaneDistance());
        }
        HandOutline.composite();
    }

}
