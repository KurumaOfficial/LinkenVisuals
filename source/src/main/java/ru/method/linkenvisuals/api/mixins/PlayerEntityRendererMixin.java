package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.accessors.ICapeRenderState;
import ru.method.linkenvisuals.api.accessors.ICapeSimulationHolder;
import ru.method.linkenvisuals.cosmetics.preview.CosmeticPreviewState;
import ru.method.linkenvisuals.cosmetics.wave.CapeSimulation;
import ru.method.linkenvisuals.cosmetics.render.CosmeticAttachmentFeatureRenderer;
import ru.method.linkenvisuals.cosmetics.service.CosmeticsService;
import ru.method.linkenvisuals.modules.impl.render.SelfNametags;
import ru.method.linkenvisuals.utils.text.StreamerModeUtils;
import ru.method.linkenvisuals.utils.text.SyncedUserTextUtils;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityRenderState, PlayerEntityModel> {

    protected PlayerEntityRendererMixin(EntityRendererFactory.Context ctx, PlayerEntityModel model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void linkenvisuals$character(net.minecraft.entity.PlayerLikeEntity player, PlayerEntityRenderState state, float delta, CallbackInfo ci) {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        CosmeticsService service = instance == null ? null : instance.getCosmeticsService();
        boolean selected = service != null && service.shouldRender(player.getUuid(), ((AbstractClientPlayerEntity) player).getGameProfile().name())
                && "character_rudy".equals(service.resolveDefinition(ru.method.linkenvisuals.cosmetics.model.CosmeticSlot.CHARACTER,
                        player.getUuid(), ((AbstractClientPlayerEntity) player).getGameProfile().name()).id())
                && ru.method.linkenvisuals.cosmetics.character.RudyCharacter.available();
        ((ru.method.linkenvisuals.api.accessors.ICharacterRenderState) state).linkenvisuals$setRudy(selected);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void linkenvisuals$registerCosmeticFeatures(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
        this.addFeature(new CosmeticAttachmentFeatureRenderer(this));
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("RETURN"))
    private void linkenvisuals$applyCosmeticCape(net.minecraft.entity.PlayerLikeEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (player == null || state == null || state.skinTextures == null) {
            return;
        }
        LinkenVisuals instance = LinkenVisuals.getInstance();
        CosmeticsService service = instance == null ? null : instance.getCosmeticsService();
        Identifier cape = service == null
                ? null
                : service.resolveCapeTexture(player.getUuid(), ((AbstractClientPlayerEntity) player).getGameProfile().name());
        if (cape != null) {
            SkinTextures original = state.skinTextures;
            state.skinTextures = new SkinTextures(
                    original.body(),
                    linkenvisuals$asset(cape),
                    original.elytra(),
                    original.model(),
                    original.secure()
            );
            // Ваниль в updateRenderState ставит capeVisible = (есть ванильный плащ) && (часть CAPE не скрыта)
            // ЕЩЁ ДО нашего миксина. У офлайн-аккаунта ванильного плаща нет → capeVisible=false → и наш
            // wave-плащ, и ванильный рендер плаща пропускаются. Раз косметический плащ назначен явно —
            // форсим видимость (иначе плащ не рисуется ни на игроке, ни в превью).
            state.capeVisible = true;
        }
        // Физику плаща цепляем ПОСЛЕ подмены текстуры — иначе косметический плащ (у которого нет
        // ванильной текстуры) в этот кадр посчитался бы «без плаща» и остался бы статичным.
        if (player instanceof AbstractClientPlayerEntity clientPlayer) {
            linkenvisuals$attachCapeSimulation(clientPlayer, state, tickDelta);
        }

        Identifier allSkinsOverride = ru.method.linkenvisuals.modules.impl.render.AllSkins.skinOverride();
        if (allSkinsOverride != null && state.skinTextures != null) {
            state.skinTextures = new SkinTextures(
                    linkenvisuals$asset(allSkinsOverride),
                    state.skinTextures.cape(),
                    state.skinTextures.elytra(),
                    state.skinTextures.model(),
                    state.skinTextures.secure()
            );
        }
    }

    @Inject(
            method = "getTexture(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)Lnet/minecraft/util/Identifier;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void linkenvisuals$allSkinsGetTexture(PlayerEntityRenderState state, CallbackInfoReturnable<Identifier> cir) {
        Identifier override = ru.method.linkenvisuals.modules.impl.render.AllSkins.skinOverride();
        if (override != null) {
            cir.setReturnValue(override);
        }
    }

    /**
     * Физика плаща живёт на игроке, а рисует её фича-рендерер, который сущность уже не видит —
     * прокидываем ссылку и всё нужное для позы в стейт. Цепочка строится лениво здесь же: у игроков,
     * которых мы ни разу не рисовали с плащом, симуляция не тикает вообще.
     */
    private void linkenvisuals$attachCapeSimulation(AbstractClientPlayerEntity player, PlayerEntityRenderState state, float tickDelta) {
        if (!(state instanceof ICapeRenderState capeState)) {
            return;
        }
        if (state.invisible || !state.capeVisible || state.skinTextures == null
                || state.skinTextures.cape() == null) {
            capeState.linkenvisuals$setCapeSimulation(null);
            return;
        }
        CapeSimulation simulation = ((ICapeSimulationHolder) player).linkenvisuals$capeSimulation();
        simulation.build(player);
        capeState.linkenvisuals$setCapeSimulation(simulation);
        capeState.linkenvisuals$setCapeTickDelta(tickDelta);
        // Своё приседание, а не state.sneaking: SelfNametags обнуляет его у локального игрока.
        capeState.linkenvisuals$setCapeCrouching(player.isInSneakingPose());
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("RETURN"))
    private void linkenvisuals$applyAllSkins(net.minecraft.entity.PlayerLikeEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (state == null || state.skinTextures == null) {
            return;
        }
        Identifier override = ru.method.linkenvisuals.modules.impl.render.AllSkins.skinOverride();
        if (override == null) {
            return;
        }
        SkinTextures o = state.skinTextures;
        // Подмена текстуры скина у всех игроков, остальное (плащ/элитры/модель) сохраняем.
        state.skinTextures = new SkinTextures(
                linkenvisuals$asset(override),
                o.cape(),
                o.elytra(),
                o.model(),
                o.secure()
        );
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("RETURN"))
    private void linkenvisuals$sanitizeSelfRenderState(net.minecraft.entity.PlayerLikeEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (state == null) {
            return;
        }
        if (CosmeticPreviewState.suppressesEntityLabel()) {
            state.displayName = null;
            state.playerName = null;
            return;
        }
        LinkenVisuals instance = LinkenVisuals.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        if (instance == null || client == null || client.player == null || player != client.player) {
            return;
        }
        SelfNametags selfNametags = instance.getModuleManager().getModule(SelfNametags.class);
        boolean enabled = selfNametags != null && selfNametags.isToggle();
        if (!enabled) {
            return;
        }
        state.sneaking = false;
        if (state.displayName != null) {
            state.displayName = StreamerModeUtils.sanitizeSelfNametag(state.displayName);
        }
    }

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void linkenvisuals$skipCosmeticPreviewPlayerLabel(CallbackInfo ci) {
        if (CosmeticPreviewState.suppressesEntityLabel()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void linkenvisuals$beginSelfNametagRender(PlayerEntityRenderState state, MatrixStack matrices,
                                                      net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
                                                      net.minecraft.client.render.state.CameraRenderState cameraState,
                                                      CallbackInfo ci) {
        // Тот же HEAD занимает linkenvisuals$skipCosmeticPreviewPlayerLabel с ci.cancel(), а порядок
        // инжекторов Mixin не гарантирует. Если сначала выполнимся мы, а потом придёт cancel —
        // RETURN не наступит, глубина протечёт и останется поднятой ДО КОНЦА СЕССИИ: после этого
        // весь рисуемый текст пойдёт по пути self-неймтега. Проверяем то же условие сами.
        if (CosmeticPreviewState.suppressesEntityLabel()) {
            return;
        }
        if (linkenvisuals$isSelfNametagState(state)) {
            StreamerModeUtils.beginSelfNametagRender();
        }
    }

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("RETURN"),
            require = 0
    )
    private void linkenvisuals$endSelfNametagRender(PlayerEntityRenderState state, MatrixStack matrices,
                                                    net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
                                                    net.minecraft.client.render.state.CameraRenderState cameraState,
                                                    CallbackInfo ci) {
        if (linkenvisuals$isSelfNametagState(state)) {
            StreamerModeUtils.endSelfNametagRender();
        }
    }


    private static AssetInfo.TextureAsset linkenvisuals$asset(Identifier id) {
        return new AssetInfo.TextureAsset() {
            @Override
            public Identifier id() {
                return id;
            }

            @Override
            public Identifier texturePath() {
                return id;
            }
        };
    }

    private boolean linkenvisuals$isSelfNametagState(PlayerEntityRenderState state) {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        if (instance == null || client == null || client.player == null || state == null) {
            return false;
        }
        SelfNametags selfNametags = instance.getModuleManager().getModule(SelfNametags.class);
        String selfName = client.player.getGameProfile().name();
        return selfNametags != null
                && selfNametags.isToggle()
                && selfName != null
                && state.playerName != null
                && state.playerName.getString().equalsIgnoreCase(selfName);
    }
}
