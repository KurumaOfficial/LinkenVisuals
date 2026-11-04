package ru.method.linkenvisuals.cosmetics.service;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.cosmetics.catalog.CosmeticCatalog;
import ru.method.linkenvisuals.cosmetics.model.CosmeticDefinition;
import ru.method.linkenvisuals.cosmetics.model.CosmeticProfile;
import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;
import ru.method.linkenvisuals.cosmetics.persistence.CosmeticsRepository;
import ru.method.linkenvisuals.cosmetics.preview.CosmeticPreviewState;
import ru.method.linkenvisuals.managers.misc.UserSyncManager;
import ru.method.linkenvisuals.utils.Wrapper;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class CosmeticsService implements Wrapper {
    private final CosmeticsRepository repository = new CosmeticsRepository();
    private volatile boolean enabled = true;
    // Чья косметика видна: все синхронизированные / друзья / пати / только своя.
    private volatile ru.method.linkenvisuals.cosmetics.model.CosmeticVisibility visibility =
            ru.method.linkenvisuals.cosmetics.model.CosmeticVisibility.EVERYONE;

    public CosmeticsRepository repository() {
        return repository;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Показывается ли ХОТЬ ЧЬЯ-ТО чужая косметика (быстрый ранний выход для рендеров). */
    public boolean isShowSyncedUsers() {
        return visibility != ru.method.linkenvisuals.cosmetics.model.CosmeticVisibility.SELF_ONLY;
    }

    public void setVisibility(ru.method.linkenvisuals.cosmetics.model.CosmeticVisibility visibility) {
        this.visibility = visibility == null
                ? ru.method.linkenvisuals.cosmetics.model.CosmeticVisibility.EVERYONE
                : visibility;
    }

    public CosmeticProfile getLocalProfile() {
        return repository.getLocalProfile();
    }

    public void applyLocalSelection(CosmeticSlot slot, String cosmeticId) {
        if (slot == null || !CosmeticCatalog.isAllowed(cosmeticId, slot)) {
            return;
        }
        repository.setLocalProfile(repository.getLocalProfile().with(slot, cosmeticId));
        if (slot == CosmeticSlot.EFFECT) {
            LinkenVisuals instance = LinkenVisuals.getInstance();
            if (instance != null && instance.getModuleManager() != null) {
                ru.method.linkenvisuals.modules.impl.player.CosmeticsModule module =
                        instance.getModuleManager().getModule(ru.method.linkenvisuals.modules.impl.player.CosmeticsModule.class);
                if (module != null && module.getEffects() != null) {
                    module.getEffects().applyEffectSelection(cosmeticId);
                }
            }
        }
        pushLocalProfileAsync();
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance != null && !instance.isConfigLoading()) {
            instance.getConfigManager().forceSave();
        }
    }

    /** Применить кастомный плащ с laby.net (полная ссылка или голый ID) — тянется в фоне, появится по готовности. */
    public void applyLabyCape(String urlOrId) {
        applyLabyCapeId(ru.method.linkenvisuals.cosmetics.laby.LabyCapeManager.parseId(urlOrId));
    }

    /**
     * Применить УЖЕ разобранный laby-id (клик по «Custom Cape» в сетке). Без повторного parseId: тот голый
     * 16-hex id НЕ принимает (ждёт 32-hex подряд или путь capes/&lt;id&gt;), из-за чего плащ из сетки не ставился.
     */
    public void applyLabyCapeId(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        applyLocalSelection(CosmeticSlot.CAPE, "laby:" + id);
        ru.method.linkenvisuals.cosmetics.laby.LabyCapeManager.addKnown(id);   // в сетку «Custom Cape»
        ru.method.linkenvisuals.cosmetics.laby.LabyCapeManager.textureFor(id); // прогрев загрузки
    }

    public void applyLocalWingsColor(int argb) {
        applyLocalTint(CosmeticSlot.WINGS, argb);
    }

    /** Применяет цвет (тинт) к произвольному слоту локального профиля (крылья, эффекты и т.д.). */
    public void applyLocalTint(CosmeticSlot slot, int argb) {
        repository.setLocalProfile(repository.getLocalProfile().withTint(slot, argb));
        pushLocalProfileAsync();
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance != null && !instance.isConfigLoading()) {
            instance.getConfigManager().forceSave();
        }
    }

    public int resolveTint(CosmeticSlot slot, @Nullable UUID uuid, @Nullable String name) {
        return resolveProfile(uuid, name).getTint(slot);
    }

    public CosmeticProfile resolveProfile(@Nullable UUID uuid, @Nullable String name) {
        CosmeticPreviewState.Snapshot preview = CosmeticPreviewState.current();
        if (preview != null) {
            return preview.profile();
        }
        if (uuid != null && isSelf(uuid)) {
            return repository.getLocalProfile();
        }
        CosmeticProfile remote = repository.getRemoteProfile(uuid, name);
        if (!remote.isEmpty()) {
            return remote;
        }
        return new CosmeticProfile();
    }

    public CosmeticDefinition resolveDefinition(CosmeticSlot slot, @Nullable UUID uuid, @Nullable String name) {
        CosmeticProfile profile = resolveProfile(uuid, name);
        return CosmeticCatalog.resolve(profile.get(slot), slot);
    }

    @Nullable
    public Identifier resolveCapeTexture(@Nullable UUID uuid, @Nullable String name) {
        if (!shouldRender(uuid, name)) { // shouldRender сам учитывает enabled + preview-байпас
            return null;
        }
        String capeId = resolveProfile(uuid, name).get(CosmeticSlot.CAPE);
        if (ru.method.linkenvisuals.cosmetics.linken.LinkenCape.ID.equals(capeId)) {
            return ru.method.linkenvisuals.cosmetics.linken.LinkenCape.texture();
        }
        if (capeId != null && capeId.startsWith("laby:")) { // кастомный плащ laby.net
            return ru.method.linkenvisuals.cosmetics.laby.LabyCapeManager.textureFor(capeId.substring(5));
        }
        CosmeticDefinition definition = CosmeticCatalog.resolve(capeId, CosmeticSlot.CAPE);
        return definition.isNone() ? null : definition.texture();
    }

    public boolean shouldRender(@Nullable UUID uuid, @Nullable String name) {
        // Превью в GUI показывает косметику НЕЗАВИСИМО от тумблера модуля — байпас идёт ПЕРВЫМ,
        // иначе при выключенном модуле (enabled=false) гардероб не рисовался в превью.
        if (CosmeticPreviewState.isActive()) {
            return true;
        }
        if (!enabled) {
            return false;
        }
        if (uuid != null && isSelf(uuid)) {
            return true;
        }
        // getInstance() может быть null на ранних/поздних стадиях жизненного цикла — проверяем, как в isFriend.
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null) {
            return false;
        }
        UserSyncManager syncManager = instance.getUserSyncManager();
        if (syncManager == null || !syncManager.isSyncedUser(uuid, name)) {
            return false;
        }
        // Фильтр «чья косметика видна»: все / друзья / пати / только своя.
        return switch (visibility) {
            case EVERYONE -> true;
            case FRIENDS -> isFriend(name);
            case PARTY -> syncManager.isPartyMember(uuid, name);
            case SELF_ONLY -> false;
        };
    }

    private boolean isFriend(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getFriendManager() == null) {
            return false;
        }
        return instance.getFriendManager().isFriend(name);
    }

    public boolean hasAttachment(CosmeticSlot slot, @Nullable UUID uuid, @Nullable String name) {
        if (!shouldRender(uuid, name)) {
            return false;
        }
        return !resolveDefinition(slot, uuid, name).isNone();
    }

    public void clearRemoteProfiles() {
        repository.clearRemoteProfiles();
    }

    public void pushLocalProfileAsync() {
        UserSyncManager syncManager = LinkenVisuals.getInstance().getUserSyncManager();
        if (syncManager != null) {
            syncManager.requestCosmeticsUpdate(null, null);
        }
    }

    private boolean isSelf(UUID uuid) {
        if (mc.player == null || mc.player.getGameProfile() == null) {
            return false;
        }
        UUID self = mc.player.getGameProfile().id();
        return self != null && self.equals(uuid);
    }
}
