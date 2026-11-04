package ru.method.linkenvisuals.screen.cosmetics;



import net.minecraft.client.resource.language.I18n;

import net.minecraft.util.Identifier;

import org.jetbrains.annotations.Nullable;

import ru.method.linkenvisuals.LinkenVisuals;

import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;



public enum CosmeticsTab {

    CHARACTER(CosmeticSlot.CHARACTER, "cosmetics.slot.character", TabIconKind.RUN, null),

    CAPE(CosmeticSlot.CAPE, "cosmetics.slot.cape", TabIconKind.CAPE, null),

    HAT(CosmeticSlot.HAT, "cosmetics.slot.hat", TabIconKind.HAT, null),

    HEAD(CosmeticSlot.HEAD, "cosmetics.slot.head", TabIconKind.HAT, null),

    BODY(CosmeticSlot.BODY, "cosmetics.slot.body", TabIconKind.TEXTURE, null),

    SHIELD(CosmeticSlot.SHIELD, "cosmetics.slot.shield", TabIconKind.TEXTURE, null),

    WINGS(CosmeticSlot.WINGS, "cosmetics.slot.wings", TabIconKind.WINGS, null),

    EFFECT(null, "cosmetics.slot.effect", TabIconKind.SPARKLE, LinkenVisuals.id("cosmetics/icons/effect_halo.png")),

    PET(null, "cosmetics.slot.pet", TabIconKind.PAW, null),

    ANIMATION(null, "cosmetics.slot.animation", TabIconKind.RUN, null);



    public enum TabIconKind {

        TEXTURE,

        CAPE,

        HAT,

        WINGS,

        SPARKLE,

        PAW,

        RUN,

        WAND

    }



    private final CosmeticSlot wardrobeSlot;

    private final String titleKey;

    private final TabIconKind iconKind;

    @Nullable

    private final Identifier iconTexture;



    CosmeticsTab(CosmeticSlot wardrobeSlot, String titleKey, TabIconKind iconKind, @Nullable Identifier iconTexture) {

        this.wardrobeSlot = wardrobeSlot;

        this.titleKey = titleKey;

        this.iconKind = iconKind;

        this.iconTexture = iconTexture;

    }



    public CosmeticSlot wardrobeSlot() {

        return wardrobeSlot;

    }



    public String titleKey() {

        return titleKey;

    }



    public String label() {

        return I18n.translate(titleKey);

    }



    public TabIconKind iconKind() {

        return iconKind;

    }



    @Nullable

    public Identifier iconTexture() {

        return iconTexture;

    }



    public boolean isWardrobe() {

        return wardrobeSlot != null;

    }

}


