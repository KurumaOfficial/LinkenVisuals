package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.api.accessors.ICapeSimulationHolder;
import ru.method.linkenvisuals.cosmetics.wave.CapeSimulation;

/**
 * Состояние физики плаща живёт на самом игроке и тикается там же, где движок обновляет отставание
 * плаща ({@code updateCapeAngles}) — то есть ровно раз в тик и уже с актуальными capeX/capeZ.
 *
 * <p>Симуляция ленивая: цепочка строится только когда плащ реально рисуется (см. PlayerEntityRendererMixin),
 * до этого {@code tick} ничего не делает. Игроки без плаща не стоят клиенту ни такта.</p>
 */
@Mixin(PlayerEntity.class)
public class PlayerEntityCapeMixin implements ICapeSimulationHolder {

    // НЕ инлайн-инициализируем (было `= new CapeSimulation()`): инициализатор @Unique-поля живёт в
    // конструкторе миксина, а ProGuard в релизе удаляет его как «мёртвый» (миксин-класс не инстанцируется)
    // → Mixin вливает поле без инициализатора → в обфе оно null → NPE в тике. Создаём лениво в геттере.
    @Unique
    private CapeSimulation linkenvisuals$capeSimulation;

    @Override
    public CapeSimulation linkenvisuals$capeSimulation() {
        if (linkenvisuals$capeSimulation == null) {
            linkenvisuals$capeSimulation = new CapeSimulation();
        }
        return linkenvisuals$capeSimulation;
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void linkenvisuals$tickCapeSimulation(CallbackInfo ci) {
        if ((Object) this instanceof AbstractClientPlayerEntity player) {
            linkenvisuals$capeSimulation().tick(player);   // через геттер — ленивая инициализация
        }
    }
}
