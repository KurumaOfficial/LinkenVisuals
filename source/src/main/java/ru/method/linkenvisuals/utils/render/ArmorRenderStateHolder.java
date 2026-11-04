package ru.method.linkenvisuals.utils.render;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;

public final class ArmorRenderStateHolder {

    private static final ThreadLocal<LivingEntityRenderState> ARMOR_STATE = new ThreadLocal<>();

    private ArmorRenderStateHolder() {
    }

    public static void set(LivingEntityRenderState state) {
        ARMOR_STATE.set(state);
    }

    public static LivingEntityRenderState get() {
        return ARMOR_STATE.get();
    }

    public static void clear() {
        ARMOR_STATE.remove();
    }
}
