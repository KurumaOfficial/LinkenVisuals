package ru.method.linkenvisuals.modules.impl.render;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import ru.method.linkenvisuals.api.events.impl.EventTick;
import ru.method.linkenvisuals.modules.api.Category;
import ru.method.linkenvisuals.modules.api.Module;
import ru.method.linkenvisuals.modules.settings.api.Bind;
import ru.method.linkenvisuals.modules.settings.api.Nameable;
import ru.method.linkenvisuals.modules.settings.impl.BindSetting;
import ru.method.linkenvisuals.modules.settings.impl.EnumSetting;

public class Freelook extends Module {

    private final BindSetting freelookBind = new BindSetting("settings.freelook.bind", new Bind(GLFW.GLFW_KEY_F5, false));
    private final EnumSetting<ActivationMode> activationMode = new EnumSetting<>("settings.freelook.mode", ActivationMode.HOLD);

    private boolean active;
    private float cameraYaw;
    private float cameraPitch;
    private Perspective previousPerspective = Perspective.FIRST_PERSON;
    private boolean mouseUpdateCaptured;
    private float capturedYaw;
    private float capturedPitch;
    private float capturedPrevYaw;
    private float capturedPrevPitch;
    private float capturedBodyYaw;
    private float capturedPrevBodyYaw;
    private float capturedHeadYaw;
    private float capturedPrevHeadYaw;

    public Freelook() {
        super("Freelook", Category.Visuals);
        getSettings().add(freelookBind);
        getSettings().add(activationMode);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        refreshState();
    }

    @Override
    public void onDisable() {
        stopFreelook();
        super.onDisable();
    }

    @EventHandler
    private void onTick(EventTick event) {
        refreshState();
    }

    public void refreshState() {
        if (!isToggled() || fullNullCheck() || mc.currentScreen != null) {
            stopFreelook();
            return;
        }

        if (activationMode.getValue() == ActivationMode.HOLD && !isFreelookKeyDown()) {
            stopFreelook();
        }

        if (active && mc.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
    }

    public void handleKeyboardInput(int key, int action) {
        handleBindInput(key, false, action);
    }

    public void handleMouseInput(int button, int action) {
        handleBindInput(button, true, action);
    }

    private void handleBindInput(int code, boolean mouse, int action) {
        if (!isToggled()) {
            return;
        }

        if (fullNullCheck() || mc.currentScreen != null) {
            stopFreelook();
            return;
        }

        Bind bind = freelookBind.getValue();
        if (bind == null || bind.isEmpty() || bind.isMouse() != mouse || bind.getKey() != code) {
            return;
        }

        ActivationMode mode = activationMode.getValue();
        if (mode == ActivationMode.HOLD) {
            if (action == GLFW.GLFW_PRESS) {
                startFreelook();
            } else if (action == GLFW.GLFW_RELEASE) {
                stopFreelook();
            }
            return;
        }

        if (action == GLFW.GLFW_PRESS) {
            if (active) {
                stopFreelook();
            } else {
                startFreelook();
            }
        }
    }

    public boolean isActive() {
        return isToggled() && active && !fullNullCheck();
    }

    public boolean shouldOverrideCamera(Entity entity) {
        return entity == mc.player && isActive();
    }

    public float getCameraYaw() {
        return cameraYaw;
    }

    public float getCameraPitch() {
        return cameraPitch;
    }

    public void captureMouseUpdate(ClientPlayerEntity player) {
        if (!isActive() || player == null) {
            return;
        }

        mouseUpdateCaptured = true;
        capturedYaw = player.getYaw();
        capturedPitch = player.getPitch();
        capturedPrevYaw = player.lastYaw;
        capturedPrevPitch = player.lastPitch;
        capturedBodyYaw = player.bodyYaw;
        capturedPrevBodyYaw = player.lastBodyYaw;
        capturedHeadYaw = player.headYaw;
        capturedPrevHeadYaw = player.lastHeadYaw;
    }

    public void releaseMouseUpdate(ClientPlayerEntity player) {
        if (!mouseUpdateCaptured || player == null) {
            return;
        }

        mouseUpdateCaptured = false;

        if (active) {
            float yawDelta = MathHelper.wrapDegrees(player.getYaw() - capturedYaw);
            float pitchDelta = player.getPitch() - capturedPitch;
            cameraYaw = MathHelper.wrapDegrees(cameraYaw + yawDelta);
            cameraPitch = MathHelper.clamp(cameraPitch + pitchDelta, -90.0F, 90.0F);
        }

        player.setYaw(capturedYaw);
        player.setPitch(capturedPitch);
        player.lastYaw = capturedPrevYaw;
        player.lastPitch = capturedPrevPitch;
        player.bodyYaw = capturedBodyYaw;
        player.lastBodyYaw = capturedPrevBodyYaw;
        player.headYaw = capturedHeadYaw;
        player.lastHeadYaw = capturedPrevHeadYaw;
    }

    public boolean shouldSuppressVanillaPerspectiveKey(int key, int scancode) {
        if (!isToggled() || fullNullCheck() || mc.currentScreen != null) {
            return false;
        }

        Bind bind = freelookBind.getValue();
        if (bind == null || bind.isEmpty() || bind.isMouse()) {
            return false;
        }

        return bind.matchesKey(key) && mc.options.togglePerspectiveKey.matchesKey(new net.minecraft.client.input.KeyInput(key, scancode, 0));
    }

    private void startFreelook() {
        if (active || fullNullCheck()) {
            return;
        }

        active = true;
        previousPerspective = mc.options.getPerspective();
        cameraYaw = mc.player.getYaw();
        cameraPitch = mc.player.getPitch();
        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
    }

    private void stopFreelook() {
        if (!active) {
            return;
        }

        active = false;
        mouseUpdateCaptured = false;
        mc.options.setPerspective(previousPerspective);
        previousPerspective = Perspective.FIRST_PERSON;
    }

    private boolean isFreelookKeyDown() {
        Bind bind = freelookBind.getValue();
        if (bind == null || bind.isEmpty()) {
            return false;
        }

        long handle = mc.getWindow().getHandle();
        if (bind.isMouse()) {
            return GLFW.glfwGetMouseButton(handle, bind.getKey()) == GLFW.GLFW_PRESS;
        }

        return GLFW.glfwGetKey(handle, bind.getKey()) == GLFW.GLFW_PRESS;
    }

    private enum ActivationMode implements Nameable {
        HOLD("settings.freelook.mode.hold"),
        TOGGLE("settings.freelook.mode.toggle");

        private final String name;

        ActivationMode(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
