package ru.method.linkenvisuals.modules.impl.render;

import meteordevelopment.orbit.EventHandler;
import ru.method.linkenvisuals.api.events.impl.EventAttackEntity;
import ru.method.linkenvisuals.api.events.impl.EventRender3D;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.impl.render.particles.DecorativeParticlesFeature;
import ru.method.linkenvisuals.modules.impl.render.particles.LegacyImpactParticlesFeature;
import ru.method.linkenvisuals.modules.settings.impl.ColorSetting;
import ru.method.linkenvisuals.utils.render.ColorUtils;

import java.awt.Color;

public class DamageParticles extends Module {
    private final ColorSetting particleColor = new ColorSetting("settings.damageparticles.color", ColorUtils.getGlobalColor());
    private final LegacyImpactParticlesFeature legacyParticles = new LegacyImpactParticlesFeature(this::getParticleColor);
    private final DecorativeParticlesFeature decorativeParticles = new DecorativeParticlesFeature(this::getParticleColor);

    public DamageParticles() {
        super("DamageParticles", Category.Visuals);
        getSettings().add(particleColor);
        getSettings().addAll(legacyParticles.getSettings());
        getSettings().addAll(decorativeParticles.getSettings());
    }

    private Color getParticleColor() {
        Color color = particleColor.getValue();
        return color == null ? ColorUtils.getGlobalColor() : color;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        legacyParticles.clear();
        decorativeParticles.reset();
    }

    @Override
    public void onDisable() {
        legacyParticles.clear();
        decorativeParticles.reset();
        super.onDisable();
    }

    @EventHandler
    private void onAttackEntity(EventAttackEntity event) {
        if (fullNullCheck() || event.getTarget() == null) return;
        legacyParticles.spawn(event.getTarget());
        decorativeParticles.onAttack(event.getTarget());
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (fullNullCheck()) return;
        decorativeParticles.onTick();
    }

    @EventHandler
    private void onRender3D(EventRender3D.Game event) {
        if (fullNullCheck()) return;
        legacyParticles.render();
        decorativeParticles.render(event.getMatrixStack());
    }
}
