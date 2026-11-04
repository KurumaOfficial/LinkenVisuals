package ru.method.linkenvisuals.utils.render;

import meteordevelopment.orbit.IEventBus;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import ru.method.linkenvisuals.api.events.impl.EventRender2D;

public final class HudRenderFrame {

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private HudRenderFrame() {
    }

    public static void begin(DrawContext context, RenderTickCounter tickCounter) {
        State state = STATE.get();
        state.context = context;
        state.tickCounter = tickCounter;
        state.hudRenderedBeforeOverlays = false;
        state.hudEventPosted = false;
        state.dynamicIslandDeferredToTopPass = false;
        state.dynamicIslandTopPass = false;
        state.active = true;
    }

    public static void end() {
        State state = STATE.get();
        state.active = false;
        state.context = null;
        state.tickCounter = null;
        state.hudRenderedBeforeOverlays = false;
        state.hudEventPosted = false;
        state.dynamicIslandDeferredToTopPass = false;
        state.dynamicIslandTopPass = false;
    }

    public static State get() {
        State state = STATE.get();
        return state.active ? state : null;
    }

    /**
     * Dispatches the HUD render event once per frame before vanilla overlays (scoreboard / tab).
     */
    public static void tryDispatchBeforeOverlays(IEventBus bus) {
        State state = get();
        if (state == null || !state.markHudRenderedBeforeOverlays()) {
            return;
        }
        dispatchHudRender(bus);
    }

    /**
     * Dispatches the HUD render event once per frame (idempotent).
     */
    public static void dispatchHudRender(IEventBus bus) {
        State state = get();
        if (state == null || !state.tryBeginHudEventPost()) {
            return;
        }
        var profiler = net.minecraft.util.profiler.Profilers.get();
        profiler.push("linkenvisuals_hud");
        try {
            profiler.push("prepare");
            try { Render2D.prepareHudFrame(state.context()); }
            finally { profiler.pop(); }
            profiler.push("widgets");
            try { bus.post(new EventRender2D(state.context(), state.tickCounter())); }
            finally { profiler.pop(); }
        } finally { profiler.pop(); }
    }

    public static final class State {
        private boolean active;
        private DrawContext context;
        private RenderTickCounter tickCounter;
        private boolean hudRenderedBeforeOverlays;
        private boolean hudEventPosted;
        private boolean dynamicIslandDeferredToTopPass;
        private boolean dynamicIslandTopPass;

        private State() {
        }

        public DrawContext context() {
            return context;
        }

        public RenderTickCounter tickCounter() {
            return tickCounter;
        }

        public boolean markHudRenderedBeforeOverlays() {
            if (hudRenderedBeforeOverlays) {
                return false;
            }
            hudRenderedBeforeOverlays = true;
            dynamicIslandDeferredToTopPass = true;
            return true;
        }

        public boolean hudRenderedBeforeOverlays() {
            return hudRenderedBeforeOverlays;
        }

        public boolean tryBeginHudEventPost() {
            if (hudEventPosted) {
                return false;
            }
            hudEventPosted = true;
            return true;
        }

        public boolean hudEventPosted() {
            return hudEventPosted;
        }

        public boolean shouldDeferDynamicIsland() {
            return dynamicIslandDeferredToTopPass && !dynamicIslandTopPass;
        }

        public boolean needsDynamicIslandTopPass() {
            return dynamicIslandDeferredToTopPass && !dynamicIslandTopPass;
        }

        public void beginDynamicIslandTopPass() {
            dynamicIslandTopPass = true;
        }
    }
}
