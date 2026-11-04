package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ru.method.linkenvisuals.api.accessors.ICharacterRenderState;

@Mixin(PlayerEntityRenderState.class)
public class CharacterRenderStateMixin implements ICharacterRenderState, dev.linkenvisuals.render.state.RudyRenderState {
    @Unique private boolean linkenvisuals$rudy;
    public boolean linkenvisuals$isRudy() { return linkenvisuals$rudy; }
    public void linkenvisuals$setRudy(boolean enabled) { linkenvisuals$rudy = enabled; }
    public String linkenvisuals$getCustomCharacter() { return linkenvisuals$rudy ? "Rudy" : null; }
    public void linkenvisuals$setCustomCharacter(String character) { linkenvisuals$rudy = "Rudy".equals(character); }
    public boolean linkenvisuals$isAirborne() { return false; }
    public void linkenvisuals$setAirborne(boolean airborne) { }
}
