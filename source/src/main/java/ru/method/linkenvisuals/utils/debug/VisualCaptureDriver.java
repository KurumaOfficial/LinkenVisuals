package ru.method.linkenvisuals.utils.debug;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import ru.method.linkenvisuals.LinkenVisuals;

/**
 * Developer-only visual regression capture. Enabled with
 * {@code -Dlinkenvisuals.visualtest=true}; it never runs in normal sessions.
 */
public final class VisualCaptureDriver {

    private static int ticks;
    private static boolean finished;
    private static int overlayTargetX;
    private static int overlayTargetY;
    private static int overlayTargetZ;
    private static int overlayGroundY;

    private VisualCaptureDriver() {
    }

    public static void tick(MinecraftClient client) {
        if (!Boolean.getBoolean("linkenvisuals.visualtest") || client == null || finished) {
            return;
        }
        if (Boolean.getBoolean("linkenvisuals.visualtest.menu")) {
            if (client.currentScreen == null) {
                return;
            }
            ticks++;
            if (ticks == 70) {
                shot(client, "visual-menu-intro.png");
            } else if (ticks == 220) {
                shot(client, "visual-menu.png");
            } else if (ticks == 300) {
                shot(client, "visual-menu-late.png");
                finished = true;
                if (Boolean.getBoolean("linkenvisuals.visualtest.quit")) {
                    client.scheduleStop();
                }
            }
            return;
        }
        if (Boolean.getBoolean("linkenvisuals.visualtest.singleplayer.title")) {
            ticks++;
            if (ticks == 100) {
                client.setScreen(new ru.method.linkenvisuals.screen.world.CustomSingleplayerScreen(null));
            } else if (ticks == 170) {
                shot(client, "visual-singleplayer-title.png");
            } else if (ticks == 190) {
                finished = true;
                if (Boolean.getBoolean("linkenvisuals.visualtest.quit")) {
                    client.scheduleStop();
                }
            }
            return;
        }
        if (client.world == null || client.player == null) {
            return;
        }
        ticks++;
        if (Boolean.getBoolean("linkenvisuals.visualtest.cosmetics")) {
            if (ticks == 10) {
                // Control frame: same spot, same camera, cosmetics still off.
                client.player.setPitch(15f);
                client.player.setYaw(45f);
                client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
                client.player.networkHandler.sendChatCommand("time set day");
            } else if (ticks == 14) {
                shot(client, "visual-cosmetics-before.png");
            } else if (ticks == 20) {
                LinkenVisuals instance = LinkenVisuals.getInstance();
                if (instance != null && instance.getModuleManager() != null) {
                    ru.method.linkenvisuals.modules.impl.player.CosmeticsModule cosmetics =
                            instance.getModuleManager().getModule(ru.method.linkenvisuals.modules.impl.player.CosmeticsModule.class);
                    if (cosmetics != null) cosmetics.setToggled(true);
                }
                // Проверяем не только эффекты, но и нательные косметики: шляпа, голова, крылья.
                if (instance != null && instance.getCosmeticsService() != null) {
                    var service = instance.getCosmeticsService();
                    service.applyLocalSelection(ru.method.linkenvisuals.cosmetics.model.CosmeticSlot.HAT, "hat_crown");
                    service.applyLocalSelection(ru.method.linkenvisuals.cosmetics.model.CosmeticSlot.HEAD, "head_headphones");
                    service.applyLocalSelection(ru.method.linkenvisuals.cosmetics.model.CosmeticSlot.WINGS, "wings_angel");
                    service.applyLocalSelection(ru.method.linkenvisuals.cosmetics.model.CosmeticSlot.CAPE, "cape_classic");
                }
            } else if (ticks == 70) {
                shot(client, "visual-cosmetics.png");
            } else if (ticks == 75) {
                // Второй кадр через ~250 мс — проверка анимации плаща (дождь/пульс).
                shot(client, "visual-cosmetics-anim.png");
            } else if (ticks == 80) {
                LinkenVisuals instance = LinkenVisuals.getInstance();
                if (instance != null && instance.getModuleManager() != null) {
                    ru.method.linkenvisuals.modules.impl.player.CosmeticsModule cosmetics =
                            instance.getModuleManager().getModule(ru.method.linkenvisuals.modules.impl.player.CosmeticsModule.class);
                    if (cosmetics != null) cosmetics.setToggled(false);
                }
                if (instance != null && instance.getCosmeticsService() != null) {
                    var service = instance.getCosmeticsService();
                    service.applyLocalSelection(ru.method.linkenvisuals.cosmetics.model.CosmeticSlot.HAT, "none");
                    service.applyLocalSelection(ru.method.linkenvisuals.cosmetics.model.CosmeticSlot.HEAD, "none");
                    service.applyLocalSelection(ru.method.linkenvisuals.cosmetics.model.CosmeticSlot.WINGS, "none");
                    service.applyLocalSelection(ru.method.linkenvisuals.cosmetics.model.CosmeticSlot.CHARACTER, "none");
                }
            } else if (ticks == 100) {
                shot(client, "visual-cosmetics-after-off.png");
            } else if (ticks == 140) {
                shot(client, "visual-cosmetics-late.png");
            } else if (ticks == 160) {
                finished = true;
                if (Boolean.getBoolean("linkenvisuals.visualtest.quit")) {
                    client.scheduleStop();
                }
            }
            return;
        }
        if (Boolean.getBoolean("linkenvisuals.visualtest.pet")) {
            if (ticks == 10) {
                client.options.pauseOnLostFocus = false;
                client.player.setPitch(8f);
                client.player.setYaw(0f);
                client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
                client.player.networkHandler.sendChatCommand("time set day");
                LinkenVisuals instance = LinkenVisuals.getInstance();
                if (instance != null && instance.getModuleManager() != null) {
                    ru.method.linkenvisuals.modules.impl.player.CosmeticsModule cosmetics =
                            instance.getModuleManager().getModule(ru.method.linkenvisuals.modules.impl.player.CosmeticsModule.class);
                    if (cosmetics != null) cosmetics.setToggled(true);
                }
            } else if (ticks == 45) {
                shot(client, "visual-pet-1.png");
            } else if (ticks == 75) {
                shot(client, "visual-pet-2.png");
            } else if (ticks == 110) {
                shot(client, "visual-pet-3.png");
            } else if (ticks == 130) {
                finished = true;
                if (Boolean.getBoolean("linkenvisuals.visualtest.quit")) {
                    client.scheduleStop();
                }
            }
            return;
        }
        if (Boolean.getBoolean("linkenvisuals.visualtest.wardrobe")) {
            if (ticks == 10) {
                LinkenVisuals instance = LinkenVisuals.getInstance();
                if (instance != null && instance.getModuleManager() != null) {
                    ru.method.linkenvisuals.modules.impl.player.CosmeticsModule cosmetics =
                            instance.getModuleManager().getModule(ru.method.linkenvisuals.modules.impl.player.CosmeticsModule.class);
                    if (cosmetics != null) {
                        cosmetics.setToggled(true);
                        client.setScreen(new ru.method.linkenvisuals.screen.cosmetics.CosmeticsScreen(cosmetics, null));
                    }
                }
            } else if (ticks == 35) {
                shot(client, "visual-wardrobe-cape.png");
            } else if (ticks == 45 && client.currentScreen instanceof ru.method.linkenvisuals.screen.cosmetics.CosmeticsScreen screen) {
                screen.linkenvisuals$setPreviewTab(ru.method.linkenvisuals.cosmetics.model.CosmeticSlot.HEAD);
            } else if (ticks == 60) {
                shot(client, "visual-wardrobe-head.png");
            } else if (ticks == 70 && client.currentScreen instanceof ru.method.linkenvisuals.screen.cosmetics.CosmeticsScreen screen) {
                screen.linkenvisuals$setPreviewTab(ru.method.linkenvisuals.cosmetics.model.CosmeticSlot.HAT);
            } else if (ticks == 85) {
                shot(client, "visual-wardrobe-hat.png");
            } else if (ticks == 95 && client.currentScreen instanceof ru.method.linkenvisuals.screen.cosmetics.CosmeticsScreen screen) {
                screen.linkenvisuals$setPreviewTab(ru.method.linkenvisuals.cosmetics.model.CosmeticSlot.WINGS);
            } else if (ticks == 110) {
                shot(client, "visual-wardrobe-wings.png");
            } else if (ticks == 120) {
                client.setScreen(null);
                finished = true;
                if (Boolean.getBoolean("linkenvisuals.visualtest.quit")) {
                    client.scheduleStop();
                }
            }
            return;
        }
        if (Boolean.getBoolean("linkenvisuals.visualtest.singleplayer")) {
            if (ticks == 10) {
                client.setScreen(new ru.method.linkenvisuals.screen.world.CustomSingleplayerScreen(null));
            } else if (ticks == 40) {
                shot(client, "visual-singleplayer.png");
            } else if (ticks == 55) {
                client.setScreen(null);
                finished = true;
                if (Boolean.getBoolean("linkenvisuals.visualtest.quit")) {
                    client.scheduleStop();
                }
            }
            return;
        }
        if (Boolean.getBoolean("linkenvisuals.visualtest.hands")) {
            if (ticks == 10) {
                client.player.setPitch(12f);
                client.player.setYaw(45f);
                client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                client.player.networkHandler.sendChatCommand("time set day");
                setHandsState(client, true, "OwnColor");
            } else if (ticks == 25) {
                shot(client, "visual-hands-own.png");
            } else if (ticks == 35) {
                setHandsState(client, true, "Transparent");
            } else if (ticks == 50) {
                shot(client, "visual-hands-transparent.png");
            } else if (ticks == 60) {
                setHandsState(client, true, "Plasma");
            } else if (ticks == 75) {
                shot(client, "visual-hands-plasma.png");
            } else if (ticks == 85) {
                setHandsState(client, true, "Nebula");
            } else if (ticks == 100) {
                shot(client, "visual-hands-nebula.png");
            } else if (ticks == 110) {
                setHandsState(client, true, "Cobweb");
            } else if (ticks == 125) {
                shot(client, "visual-hands-cobweb.png");
            } else if (ticks == 135) {
                setHandsState(client, false, "Transparent");
                setFogBlurState(client, true, true);
            } else if (ticks == 160) {
                shot(client, "visual-fog-rainbow.png");
            } else if (ticks == 170) {
                setFogBlurState(client, true, false);
            } else if (ticks == 195) {
                shot(client, "visual-fog-solid.png");
            } else if (ticks == 205) {
                setFogBlurState(client, false, false);
                finished = true;
                if (Boolean.getBoolean("linkenvisuals.visualtest.quit")) {
                    client.scheduleStop();
                }
            }
            return;
        }
        if (Boolean.getBoolean("linkenvisuals.visualtest.overlay")) {
            client.options.pauseOnLostFocus = false;
            // Automation can lose focus at any tick; never let the pause menu
            // hide the block we are trying to capture.
            if (client.currentScreen != null) {
                client.setScreen(null);
            }
            if (ticks == 10) {
                dev.linkenvisuals.module.impl.BlockOverlayModule overlay =
                        dev.linkenvisuals.module.Modules.blockOverlay();
                if (overlay != null) {
                    overlay.info().toggleSilently(true);
                    overlay.fill.setValueSilently(true);
                    overlay.useShader.setValueSilently(true);
                    overlay.fillMode.set("Aurora");
                }
                LinkenVisuals instance = LinkenVisuals.getInstance();
                if (instance != null && instance.getModuleManager() != null) {
                    ru.method.linkenvisuals.modules.impl.render.FogBlur fog =
                            instance.getModuleManager().getModule(ru.method.linkenvisuals.modules.impl.render.FogBlur.class);
                    if (fog != null) fog.setToggled(false);
                }
                // Build a clean, deterministic target: a floating stone cube
                // above eye level so the front and bottom faces are visible.
                BlockPos base = client.player.getBlockPos();
                overlayTargetX = base.getX();
                overlayTargetY = base.getY() + 2;
                overlayTargetZ = base.getZ() + 3;
                overlayGroundY = base.getY();
                client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                client.player.networkHandler.sendChatCommand("setblock " + overlayTargetX + " "
                        + overlayTargetY + " " + overlayTargetZ + " minecraft:stone");
                client.player.networkHandler.sendChatCommand("setblock " + overlayTargetX + " "
                        + overlayGroundY + " " + overlayTargetZ + " minecraft:stone");
                client.player.networkHandler.sendChatCommand("time set noon");
                lookAt(client.player, new net.minecraft.util.math.Vec3d(
                        overlayTargetX + 0.5, overlayTargetY + 0.5, overlayTargetZ + 0.5));
            }
            if (ticks == 40) {
                LinkenVisuals.LOGGER.info("[VisualCapture] overlay target={}", client.crosshairTarget);
                shot(client, "visual-overlay-stone-angle.png");
            } else if (ticks == 50) {
                client.player.networkHandler.sendChatCommand("setblock " + overlayTargetX + " "
                        + overlayTargetY + " " + overlayTargetZ + " minecraft:stone_slab");
                lookAt(client.player, new net.minecraft.util.math.Vec3d(
                        overlayTargetX + 0.5, overlayTargetY + 0.25, overlayTargetZ + 0.5));
            } else if (ticks == 60) {
                shot(client, "visual-overlay-slab.png");
            } else if (ticks == 70) {
                client.player.networkHandler.sendChatCommand("setblock " + overlayTargetX + " "
                        + overlayTargetY + " " + overlayTargetZ + " minecraft:oak_stairs[half=bottom,facing=south]");
            } else if (ticks == 80) {
                shot(client, "visual-overlay-stairs.png");
            } else if (ticks == 90) {
                client.player.networkHandler.sendChatCommand("setblock " + overlayTargetX + " "
                        + overlayTargetY + " " + overlayTargetZ + " minecraft:oak_fence");
            } else if (ticks == 100) {
                shot(client, "visual-overlay-fence.png");
            } else if (ticks == 110) {
                client.player.networkHandler.sendChatCommand("setblock " + overlayTargetX + " "
                        + overlayTargetY + " " + overlayTargetZ + " minecraft:stone");
                setOverlayVisuals(true, true, 70f, 255f);
                setOverlayMode("Plasma");
            } else if (ticks == 120) {
                shot(client, "visual-overlay-plasma.png");
            } else if (ticks == 125) {
                lookAt(client.player, new net.minecraft.util.math.Vec3d(
                        overlayTargetX + 0.5, overlayGroundY + 0.5, overlayTargetZ + 0.5));
            } else if (ticks == 130) {
                shot(client, "visual-overlay-ground.png");
                lookAt(client.player, new net.minecraft.util.math.Vec3d(
                        overlayTargetX + 0.5, overlayTargetY + 0.5, overlayTargetZ + 0.5));
                setOverlayVisuals(true, false, 70f, 255f);
                setOverlayMode("Aurora");
            } else if (ticks == 140) {
                shot(client, "visual-overlay-gradient.png");
            } else if (ticks == 145) {
                // Remove the blocks this capture session placed in the test world.
                client.player.networkHandler.sendChatCommand("setblock " + overlayTargetX + " "
                        + overlayTargetY + " " + overlayTargetZ + " air");
                client.player.networkHandler.sendChatCommand("setblock " + overlayTargetX + " "
                        + overlayGroundY + " " + overlayTargetZ + " air");
            } else if (ticks == 150) {
                setOverlayVisuals(true, true, 70f, 255f);
                setOverlayMode("Aurora");
                finished = true;
                if (Boolean.getBoolean("linkenvisuals.visualtest.quit")) {
                    client.scheduleStop();
                }
            }
            return;
        }
        if (Boolean.getBoolean("linkenvisuals.visualtest.settings")) {
            LinkenVisuals instance = LinkenVisuals.getInstance();
            if (ticks == 60) {
                shot(client, "visual-hud-lines.png");
            } else if (ticks == 80) {
                ru.method.linkenvisuals.screen.linkgui.LinkGuiController.getInstance().open();
            } else if (ticks == 100 && instance != null && instance.getModuleManager() != null) {
                ru.method.linkenvisuals.modules.impl.player.CosmeticsModule cosmetics =
                        instance.getModuleManager().getModule(ru.method.linkenvisuals.modules.impl.player.CosmeticsModule.class);
                if (cosmetics != null) {
                    ru.method.linkenvisuals.screen.linkgui.LinkGuiSettings.open(cosmetics, 520, 320);
                }
            } else if (ticks == 112) {
                var windows = ru.method.linkenvisuals.screen.linkgui.LinkGuiSettings.getWindows();
                if (!windows.isEmpty()) {
                    var window = windows.get(0);
                    window.mouseScrolled(window.getX() + 100, window.getY() + 120, 0, -8);
                }
            } else if (ticks == 140) {
                shot(client, "visual-settings.png");
                finished = true;
                if (Boolean.getBoolean("linkenvisuals.visualtest.quit")) {
                    client.scheduleStop();
                }
            }
            return;
        }
        if (Boolean.getBoolean("linkenvisuals.visualtest.targetesp")) {
            ticks++;
            if (ticks == 10) {
                client.player.networkHandler.sendChatCommand("time set day");
                client.player.networkHandler.sendChatCommand(
                        "summon minecraft:zombie ~ ~ ~-5 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}");
                LinkenVisuals instance = LinkenVisuals.getInstance();
                if (instance != null && instance.getModuleManager() != null) {
                    ru.method.linkenvisuals.modules.impl.render.TargetEsp targetEsp =
                            instance.getModuleManager().getModule(ru.method.linkenvisuals.modules.impl.render.TargetEsp.class);
                    if (targetEsp != null) {
                        targetEsp.setToggled(true);
                    }
                }
            } else if (ticks == 20 || ticks == 30) {
                aimAtNearestZombie(client);
            } else if (ticks == 40) {
                shot(client, "visual-targetesp-original.png");
            } else if (ticks == 50) {
                setTargetEspMode(client, "Ghosts");
            } else if (ticks == 80) {
                shot(client, "visual-targetesp-ghosts.png");
            } else if (ticks == 90) {
                setTargetEspMode(client, "Circles");
            } else if (ticks == 120) {
                shot(client, "visual-targetesp-circles.png");
            } else if (ticks == 130) {
                setTargetEspMode(client, "Crystals");
            } else if (ticks == 160) {
                shot(client, "visual-targetesp-crystals.png");
                finished = true;
                if (Boolean.getBoolean("linkenvisuals.visualtest.quit")) {
                    client.scheduleStop();
                }
            }
            return;
        }
        if (Boolean.getBoolean("linkenvisuals.visualtest.cursor")) {
            if (ticks == 20) {
                ru.method.linkenvisuals.screen.linkgui.LinkGuiController.getInstance().open();
            } else if (ticks >= 30 && ticks <= 56) {
                if (client.currentScreen instanceof ru.method.linkenvisuals.screen.linkgui.LinkGuiScreen screen) {
                    screen.getGuiContext().setCursorTracker(true);
                    double scale = client.getWindow().getScaleFactor();
                    double cx = client.getWindow().getWidth() * 0.5;
                    double cy = client.getWindow().getHeight() * 0.5;
                    double angle = (ticks - 30) / 26.0 * Math.PI * 1.8;
                    org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().getHandle(),
                            cx + Math.cos(angle) * 230.0 * scale,
                            cy + Math.sin(angle) * 150.0 * scale);
                }
            } else if (ticks == 58) {
                if (client.currentScreen instanceof ru.method.linkenvisuals.screen.linkgui.LinkGuiScreen screen) {
                    seedCursorTrail(screen, client);
                }
            } else if (ticks == 60) {
                shot(client, "visual-cursor.png");
                finished = true;
                if (Boolean.getBoolean("linkenvisuals.visualtest.quit")) {
                    client.scheduleStop();
                }
            }
            return;
        }
        if (ticks == 60) {
            shot(client, "visual-hud.png");
        } else if (ticks == 90) {
            ru.method.linkenvisuals.screen.linkgui.LinkGuiController.getInstance().open();
        } else if (ticks == 150) {
            shot(client, "visual-clickgui.png");
        } else if (ticks == 190) {
            shot(client, "visual-clickgui-late.png");
            finished = true;
            if (Boolean.getBoolean("linkenvisuals.visualtest.quit")) {
                client.scheduleStop();
            }
        }
    }

    private static void aimAtNearestZombie(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return;
        }
        net.minecraft.entity.Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (net.minecraft.entity.Entity entity : client.world.getEntities()) {
            if (!(entity instanceof net.minecraft.entity.mob.ZombieEntity) || !entity.isAlive()) {
                continue;
            }
            double distance = entity.squaredDistanceTo(client.player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        if (best == null) {
            return;
        }
        net.minecraft.util.math.Vec3d eye = client.player.getEyePos();
        net.minecraft.util.math.Vec3d target = best.getLerpedPos(1.0f)
                .add(0.0, best.getHeight() * 0.5, 0.0);
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.hypot(dx, dz)));
        client.player.setYaw(yaw);
        client.player.setPitch(pitch);
    }

    private static void setHandsState(MinecraftClient client, boolean enabled, String modeName) {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) {
            return;
        }
        ru.method.linkenvisuals.modules.impl.render.Hands hands =
                instance.getModuleManager().getModule(ru.method.linkenvisuals.modules.impl.render.Hands.class);
        if (hands == null) {
            return;
        }
        hands.mode.setValue(ru.method.linkenvisuals.modules.impl.render.Hands.Mode.valueOf(modeName));
        hands.setToggled(enabled);
    }

    private static void lookAt(net.minecraft.client.network.ClientPlayerEntity player, Vec3d target) {
        Vec3d eye = player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.hypot(dx, dz)));
        player.setYaw(yaw);
        player.setPitch(pitch);
    }

    private static void setOverlayVisuals(boolean fill, boolean shader,
                                          float fillAlpha, float outlineAlpha) {
        dev.linkenvisuals.module.impl.BlockOverlayModule overlay =
                dev.linkenvisuals.module.Modules.blockOverlay();
        if (overlay == null) {
            return;
        }
        overlay.fill.setValueSilently(fill);
        overlay.useShader.setValueSilently(shader);
        overlay.fillAlpha.setValue(fillAlpha);
        overlay.outlineAlpha.setValue(outlineAlpha);
    }

    private static void setOverlayMode(String modeName) {
        dev.linkenvisuals.module.impl.BlockOverlayModule overlay =
                dev.linkenvisuals.module.Modules.blockOverlay();
        if (overlay == null) {
            return;
        }
        overlay.fillMode.set(modeName);
    }

    private static void setFogBlurState(MinecraftClient client, boolean enabled, boolean rainbow) {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) {
            return;
        }
        ru.method.linkenvisuals.modules.impl.render.FogBlur fog =
                instance.getModuleManager().getModule(ru.method.linkenvisuals.modules.impl.render.FogBlur.class);
        if (fog == null) {
            return;
        }
        fog.setToggled(enabled);
        try {
            java.lang.reflect.Field rainbowField = fog.getClass().getDeclaredField("rainbow");
            rainbowField.setAccessible(true);
            ((ru.method.linkenvisuals.modules.settings.impl.BooleanSetting) rainbowField.get(fog)).setValue(rainbow);
            java.lang.reflect.Field distanceField = fog.getClass().getDeclaredField("fogDistance");
            distanceField.setAccessible(true);
            ((ru.method.linkenvisuals.modules.settings.impl.NumberSetting) distanceField.get(fog)).setValue(35.0f);
            java.lang.reflect.Field strengthField = fog.getClass().getDeclaredField("fogStrength");
            strengthField.setAccessible(true);
            ((ru.method.linkenvisuals.modules.settings.impl.NumberSetting) strengthField.get(fog)).setValue(8.0f);
        } catch (Throwable ignored) {
        }
    }

    private static void setTargetEspMode(MinecraftClient client, String modeName) {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null || instance.getModuleManager() == null) {
            return;
        }
        ru.method.linkenvisuals.modules.impl.render.TargetEsp module =
                instance.getModuleManager().getModule(ru.method.linkenvisuals.modules.impl.render.TargetEsp.class);
        if (module == null) {
            return;
        }
        try {
            java.lang.reflect.Field field = module.getClass().getDeclaredField("espMode");
            field.setAccessible(true);
            Object setting = field.get(module);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object value = Enum.valueOf((Class) Class.forName(
                    "ru.method.linkenvisuals.modules.impl.render.TargetEsp$ESPMode"), modeName);
            setting.getClass().getMethod("setValue", Object.class).invoke(setting, value);
        } catch (Throwable ignored) {
        }
    }

    private static void seedCursorTrail(ru.method.linkenvisuals.screen.linkgui.LinkGuiScreen screen, MinecraftClient client) {
        try {
            java.lang.reflect.Field field = screen.getClass().getDeclaredField("cursorTrail");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Object> list = (java.util.List<Object>) field.get(screen);
            list.clear();
            Class<?> pointClass = Class.forName("ru.method.linkenvisuals.screen.linkgui.LinkGuiCursorPoint");
            var ctor = pointClass.getDeclaredConstructor(float.class, float.class, long.class);
            ctor.setAccessible(true);
            long now = System.currentTimeMillis();
            float sw = client.getWindow().getScaledWidth();
            float sh = client.getWindow().getScaledHeight();
            for (int i = 0; i < 28; i++) {
                float t = i / 27f;
                float x = sw * 0.5f - 330f + 660f * t;
                float y = sh * 0.5f + (float) Math.sin(t * Math.PI * 2.0) * 130f;
                list.add(ctor.newInstance(x, y, now - (long) ((1f - t) * 380f)));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void shot(MinecraftClient client, String name) {
        try {
            ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), 1,
                    message -> LinkenVisuals.LOGGER.info("[VisualCapture] {}", message.getString()));
        } catch (Throwable t) {
            LinkenVisuals.LOGGER.warn("[VisualCapture] Screenshot failed", t);
        }
    }
}
