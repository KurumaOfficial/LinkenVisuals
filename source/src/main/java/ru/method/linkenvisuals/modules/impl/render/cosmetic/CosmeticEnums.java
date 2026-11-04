package ru.method.linkenvisuals.modules.impl.render.cosmetic;

import ru.method.linkenvisuals.modules.settings.api.Nameable;

public final class CosmeticEnums {

    private CosmeticEnums() {
    }

    public enum PetSkin implements Nameable {
        BAT("cosmetic.pet.bat"),
        PARROT("cosmetic.pet.parrot"),
        RAVEN("cosmetic.pet.raven"),
        FAIRY("cosmetic.pet.fairy"),
        BEE("cosmetic.pet.bee"),
        VEX("cosmetic.pet.vex"),
        FOX("cosmetic.pet.fox"),
        PIG("cosmetic.pet.pig"),
        FROG("cosmetic.pet.frog"),
        PUFFERFISH("cosmetic.pet.pufferfish"),
        SLIME("cosmetic.pet.slime");

        private final String name;

        PetSkin(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public enum HaloQuality implements Nameable {
        LOW("cosmetic.halo.quality.low"),
        MEDIUM("cosmetic.halo.quality.medium"),
        HIGH("cosmetic.halo.quality.high"),
        ULTRA("cosmetic.halo.quality.ultra");

        private final String name;

        HaloQuality(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public enum SphereMode implements Nameable {
        STATIC("cosmetic.sphere.mode.static"),
        ORBIT("cosmetic.sphere.mode.orbit"),
        SPIRAL("cosmetic.sphere.mode.spiral"),
        SPIRAL_V2("cosmetic.sphere.mode.spiral_v2"),
        WAVES("cosmetic.sphere.mode.waves");

        private final String name;

        SphereMode(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public enum SphereEffect implements Nameable {
        NONE("cosmetic.sphere.effect.none"),
        TRAIL("cosmetic.sphere.effect.trail"),
        FIRE_TRAIL("cosmetic.sphere.effect.fire_trail");

        private final String name;

        SphereEffect(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public enum SphereEditor implements Nameable {
        EXORT("cosmetic.sphere.editor.exort"),
        WEX("cosmetic.sphere.editor.wex"),
        QUAS("cosmetic.sphere.editor.quas"),
        ALL("cosmetic.sphere.editor.all");

        private final String name;

        SphereEditor(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public enum BodyTrailMode implements Nameable {
        STATIC("cosmetic.body_trail.mode.static"),
        GRADIENT("cosmetic.body_trail.mode.gradient");

        private final String name;

        BodyTrailMode(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
