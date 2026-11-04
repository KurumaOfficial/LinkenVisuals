package ru.method.linkenvisuals.cosmetics.catalog;

import ru.method.linkenvisuals.cosmetics.model.CosmeticAnchor;
import ru.method.linkenvisuals.cosmetics.model.CosmeticDefinition;
import ru.method.linkenvisuals.cosmetics.model.CosmeticEffectType;
import ru.method.linkenvisuals.cosmetics.model.CosmeticSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CosmeticCatalog {

    private static final Map<String, CosmeticDefinition> BY_ID = new LinkedHashMap<>();
    private static final Map<CosmeticSlot, List<CosmeticDefinition>> BY_SLOT = new EnumMap<>(CosmeticSlot.class);

    static {
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            BY_SLOT.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(CosmeticDefinition.none(slot));
        }

        // Фирменный анимированный плащ — первым после «нет», по умолчанию (рендерится LinkenCape,
        // texture-путь не используется — реальная текстура из FBO в CosmeticsService.resolveCapeTexture).
        register(new CosmeticDefinition("character_rudy", CosmeticSlot.CHARACTER, "cosmetics.character.rudy",
                ru.method.linkenvisuals.LinkenVisuals.id("characters/rudy_0.png"), null, null,
                CosmeticAnchor.BODY, CosmeticEffectType.NONE));
        register(CosmeticDefinition.cape("linken_signature", "cosmetics.cape.linken", "cosmetics/capes/linken_signature.png"));
        // Прочие плащи ставятся с laby.net (см. LabyCapeManager / вкладка «Плащи»).

        register(CosmeticDefinition.model("hat_crown", CosmeticSlot.HAT, "cosmetics.hat.crown",
                "cosmetics/icons/crown.png", "cosmetics/models/hat_crown.json", CosmeticAnchor.HEAD));
        register(CosmeticDefinition.model("hat_halo", CosmeticSlot.HAT, "cosmetics.hat.halo",
                "cosmetics/icons/halo.png", "cosmetics/models/hat_halo.json", CosmeticAnchor.HEAD));
        register(CosmeticDefinition.model("hat_horns", CosmeticSlot.HAT, "cosmetics.hat.horns",
                "cosmetics/icons/horns.png", "cosmetics/models/hat_horns.json", CosmeticAnchor.HEAD));
        register(CosmeticDefinition.model("hat_cap", CosmeticSlot.HAT, "cosmetics.hat.cap",
                "cosmetics/icons/cap.png", "cosmetics/models/hat_cap.json", CosmeticAnchor.HEAD));
        register(CosmeticDefinition.model("hat_caramel_frappuccino", CosmeticSlot.HAT,
                "cosmetics.hat.caramel_frappuccino", "cosmetics/models/hat_caramel_frappuccino.png",
                "cosmetics/models/hat_caramel_frappuccino.json", CosmeticAnchor.HEAD));
        register(CosmeticDefinition.model("hat_straw_hat", CosmeticSlot.HAT, "cosmetics.hat.straw_hat",
                "cosmetics/models/hat_straw_hat.png", "cosmetics/models/hat_straw_hat.json", CosmeticAnchor.HEAD));
        register(CosmeticDefinition.model("hat_top_hat", CosmeticSlot.HAT, "cosmetics.hat.top_hat",
                "cosmetics/models/hat_top_hat.png", "cosmetics/models/hat_top_hat.json", CosmeticAnchor.HEAD));

        register(CosmeticDefinition.model("head_gas_mask", CosmeticSlot.HEAD, "cosmetics.head.gas_mask",
                "cosmetics/models/head_gas_mask.png", "cosmetics/models/head_gas_mask.json", CosmeticAnchor.HEAD));
        register(CosmeticDefinition.model("head_satellite_dish", CosmeticSlot.HEAD, "cosmetics.head.satellite_dish",
                "cosmetics/models/head_satellite_dish.png", "cosmetics/models/head_satellite_dish.json", CosmeticAnchor.HEAD));
        register(CosmeticDefinition.model("head_headlamp", CosmeticSlot.HEAD, "cosmetics.head.headlamp",
                "cosmetics/models/head_headlamp.png", "cosmetics/models/head_headlamp.json", CosmeticAnchor.HEAD));
        register(CosmeticDefinition.model("head_headphones", CosmeticSlot.HEAD, "cosmetics.head.headphones",
                "cosmetics/models/head_headphones.png", "cosmetics/models/head_headphones.json", CosmeticAnchor.HEAD));
        register(CosmeticDefinition.model("head_ski_goggles", CosmeticSlot.HEAD, "cosmetics.head.ski_goggles",
                "cosmetics/models/head_ski_goggles.png", "cosmetics/models/head_ski_goggles.json", CosmeticAnchor.HEAD));
        register(CosmeticDefinition.model("head_bunny_ears", CosmeticSlot.HEAD, "cosmetics.head.bunny_ears",
                "cosmetics/models/head_bunny_ears.png", "cosmetics/models/head_bunny_ears.json", CosmeticAnchor.HEAD));

        register(CosmeticDefinition.model("body_safety_vest", CosmeticSlot.BODY, "cosmetics.body.safety_vest",
                "cosmetics/models/body_safety_vest.png", "cosmetics/models/body_safety_vest.json", CosmeticAnchor.BODY));
        register(CosmeticDefinition.model("body_kevler_vest", CosmeticSlot.BODY, "cosmetics.body.kevler_vest",
                "cosmetics/models/body_kevler_vest.png", "cosmetics/models/body_kevler_vest.json", CosmeticAnchor.BODY));
        register(CosmeticDefinition.model("body_tactical_belt", CosmeticSlot.BODY, "cosmetics.body.tactical_belt",
                "cosmetics/models/body_tactical_belt.png", "cosmetics/models/body_tactical_belt.json", CosmeticAnchor.BODY));
        register(CosmeticDefinition.model("body_quiver", CosmeticSlot.BODY, "cosmetics.body.quiver",
                "cosmetics/models/body_quiver.png", "cosmetics/models/body_quiver.json", CosmeticAnchor.BODY));
        register(CosmeticDefinition.model("body_backpack", CosmeticSlot.BODY, "cosmetics.body.backpack",
                "cosmetics/models/body_backpack.png", "cosmetics/models/body_backpack.json", CosmeticAnchor.BODY));
        register(CosmeticDefinition.model("body_shark_fin", CosmeticSlot.BODY, "cosmetics.body.shark_fin",
                "cosmetics/models/body_shark_fin.png", "cosmetics/models/body_shark_fin.json", CosmeticAnchor.BODY));

        register(CosmeticDefinition.model("shield_knight_shield", CosmeticSlot.SHIELD, "cosmetics.shield.knight_shield",
                "cosmetics/models/shield_knight_shield.png", "cosmetics/models/shield_knight_shield.json", CosmeticAnchor.LEFT_ARM));
        register(CosmeticDefinition.model("shield_edgy_shield", CosmeticSlot.SHIELD, "cosmetics.shield.edgy_shield",
                "cosmetics/models/shield_edgy_shield.png", "cosmetics/models/shield_edgy_shield.json", CosmeticAnchor.LEFT_ARM));
        register(CosmeticDefinition.model("shield_rounded_shield", CosmeticSlot.SHIELD, "cosmetics.shield.rounded_shield",
                "cosmetics/models/shield_rounded_shield.png", "cosmetics/models/shield_rounded_shield.json", CosmeticAnchor.LEFT_ARM));
        register(CosmeticDefinition.model("shield_tactical_shield", CosmeticSlot.SHIELD, "cosmetics.shield.tactical_shield",
                "cosmetics/models/shield_tactical_shield.png", "cosmetics/models/shield_tactical_shield.json", CosmeticAnchor.LEFT_ARM));

        register(CosmeticDefinition.model("wings_angel", CosmeticSlot.WINGS, "cosmetics.wings.angel",
                "cosmetics/icons/angel.png", "cosmetics/models/wings_angel.json", CosmeticAnchor.BODY));
        register(CosmeticDefinition.model("wings_demon", CosmeticSlot.WINGS, "cosmetics.wings.demon",
                "cosmetics/icons/demon.png", "cosmetics/models/wings_demon.json", CosmeticAnchor.BODY));

        // Эффект-косметики вокруг игрока (рендерятся в мире, цвет — из тинта профиля).
        // effect_halo (нимб) убран по просьбе.
        register(CosmeticDefinition.effect("effect_spheres", "cosmetics.effect.spheres",
                "cosmetics/icons/effect_spheres.png", CosmeticEffectType.SPHERES));
    }

    private CosmeticCatalog() {
    }

    private static void register(CosmeticDefinition definition) {
        BY_ID.put(definition.id(), definition);
        BY_SLOT.computeIfAbsent(definition.slot(), slot -> new ArrayList<>()).add(definition);
    }

    public static Optional<CosmeticDefinition> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ID.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public static CosmeticDefinition resolve(String id, CosmeticSlot slot) {
        return find(id).filter(def -> def.slot() == slot).orElseGet(() -> none(slot));
    }

    public static CosmeticDefinition none(CosmeticSlot slot) {
        List<CosmeticDefinition> list = BY_SLOT.get(slot);
        return list == null || list.isEmpty() ? CosmeticDefinition.none(slot) : list.getFirst();
    }

    public static List<CosmeticDefinition> forSlot(CosmeticSlot slot) {
        List<CosmeticDefinition> list = BY_SLOT.get(slot);
        return list == null ? List.of(none(slot)) : Collections.unmodifiableList(list);
    }

    public static boolean isAllowed(String id, CosmeticSlot slot) {
        if (id == null || id.isBlank() || "none".equals(id.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        // Кастомный плащ с laby.net — «laby:<id>». В каталоге его нет, но он валиден для слота плаща.
        if (slot == CosmeticSlot.CAPE && id.startsWith("laby:")) {
            return true;
        }
        return find(id).map(def -> def.slot() == slot).orElse(false);
    }
}
