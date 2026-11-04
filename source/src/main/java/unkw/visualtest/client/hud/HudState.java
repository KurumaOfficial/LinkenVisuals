package unkw.visualtest.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HudState {
    private boolean previewMode = true;
    private double speedBps;
    private double lastX;
    private double lastZ;
    private UUID trackedPlayer;
    private boolean hasPosition;
    private long hintVisibleUntil = System.currentTimeMillis() + 5000L;
    private final Map<RegistryEntry<StatusEffect>, Long> effectReceivedOrder = new HashMap<>();
    private long nextEffectOrder;

    public void tick(MinecraftClient client, KeyBinding previewKey) {
        while (previewKey.wasPressed()) {
            previewMode = !previewMode;
            hintVisibleUntil = System.currentTimeMillis() + 2500L;
            if (client.player != null) {
                client.player.sendMessage(Text.literal(previewMode
                        ? "Тестовые данные HUD включены"
                        : "HUD показывает только реальные данные"), true);
            }
        }

        if (client.player == null) {
            trackedPlayer = null;
            hasPosition = false;
            speedBps = 0.0;
            resetEffects();
            return;
        }

        UUID playerId = client.player.getUuid();
        if (!playerId.equals(trackedPlayer)) {
            trackedPlayer = playerId;
            hasPosition = false;
            speedBps = 0.0;
            resetEffects();
        }

        trackEffects(client.player.getStatusEffects());

        double x = client.player.getX();
        double z = client.player.getZ();
        if (hasPosition) {
            double rawSpeed = Math.hypot(x - lastX, z - lastZ) * 20.0;
            if (rawSpeed <= 80.0) {
                speedBps += (rawSpeed - speedBps) * 0.25;
            } else {
                speedBps = 0.0;
            }
        }

        lastX = x;
        lastZ = z;
        hasPosition = true;
    }

    public boolean previewMode() {
        return previewMode;
    }

    public double speedBps() {
        return speedBps;
    }

    public boolean showHint() {
        return System.currentTimeMillis() < hintVisibleUntil;
    }

    void trackEffects(Iterable<StatusEffectInstance> activeEffects) {
        Set<RegistryEntry<StatusEffect>> activeTypes = new HashSet<>();
        for (StatusEffectInstance effect : activeEffects) {
            RegistryEntry<StatusEffect> type = effect.getEffectType();
            activeTypes.add(type);
            if (!effectReceivedOrder.containsKey(type)) {
                effectReceivedOrder.put(type, ++nextEffectOrder);
            }
        }
        effectReceivedOrder.keySet().removeIf(type -> !activeTypes.contains(type));
    }

    long effectOrder(RegistryEntry<StatusEffect> type) {
        return effectReceivedOrder.getOrDefault(type, 0L);
    }

    private void resetEffects() {
        effectReceivedOrder.clear();
        nextEffectOrder = 0L;
    }
}
