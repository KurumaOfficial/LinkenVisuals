package unkw.visualtest.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import unkw.visualtest.client.hud.HudState;

/**
 * Entrypoint visual-test. Цельный «полевой» HUD (стиль Field) удалён вместе со своим
 * HudRenderCallback-слоем и редактором раскладки; остался только общий стейт
 * ({@link HudState}: скорость/порядок эффектов, превью по F8) — его читают мосты
 * стиля «Карточка» ({@code VisualHud.card*}).
 */
public final class Visual_testClient implements ClientModInitializer {
    private static final HudState HUD_STATE = new HudState();

    /** Общий стейт HUD (скорость/порядок эффектов) — для мостов стиля «Карточка» (VisualHud.card*). */
    public static HudState state() {
        return HUD_STATE;
    }

    @Override
    public void onInitializeClient() {
        KeyBinding previewKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.visual-test.preview",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                KeyBinding.Category.create(net.minecraft.util.Identifier.of("visual-test", "main"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> HUD_STATE.tick(client, previewKey));
    }
}
