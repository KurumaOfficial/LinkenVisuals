package ru.method.linkenvisuals.modules.impl.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;

/**
 * Убирает задержку разбивания энд-кристаллов: пока зажата атака и прицел на кристалле,
 * бьём его каждый тик с полным сбросом кулдауна (перенос CrystalOptimizer из Kimiko).
 */
public class CrystalOptimizer extends Module {

    public CrystalOptimizer() {
        super("CrystalOptimizer", Category.Combat);
    }

    @Override
    public boolean isUnsafe() {
        return true;
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (fullNullCheck() || mc.interactionManager == null || mc.currentScreen != null) return;
        if (!mc.options.attackKey.isPressed()) return;

        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof EndCrystalEntity crystal)) {
            return;
        }

        mc.player.resetTicksSinceLastAttack();
        mc.interactionManager.attackEntity(mc.player, crystal);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}
