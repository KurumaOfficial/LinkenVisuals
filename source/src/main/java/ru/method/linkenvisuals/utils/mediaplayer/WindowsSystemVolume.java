package ru.method.linkenvisuals.utils.mediaplayer;

import javax.sound.sampled.FloatControl;

/**
 * Master system volume — Core Audio, WinMM mixer, then Java Sound fallback.
 * All native paths are wrapped so volume probing never crashes the client.
 */
public final class WindowsSystemVolume {

    private static volatile float cachedVolume = 0.72f;
    private static volatile long lastReadMs;
    private static volatile FloatControl masterControl;

    private WindowsSystemVolume() {
    }

    public static float getVolume() {
        long now = System.currentTimeMillis();
        if (now - lastReadMs < 60L) {
            return cachedVolume;
        }

        if (isWindows()) {
            float core = safeRead(() -> WindowsCoreAudioVolume.readScalar());
            if (core >= 0f) {
                cachedVolume = core;
                lastReadMs = now;
                return cachedVolume;
            }
            float mixer = safeRead(() -> WindowsMixerVolume.readScalar());
            if (mixer >= 0f) {
                cachedVolume = mixer;
                lastReadMs = now;
                return cachedVolume;
            }
        }

        try {
            FloatControl control = resolveControl();
            if (control != null) {
                cachedVolume = scalarFromControl(control);
                lastReadMs = now;
            }
        } catch (Throwable ignored) {
        }
        return cachedVolume;
    }

    public static void setVolume(float volume) {
        cachedVolume = clamp(volume);
        lastReadMs = System.currentTimeMillis();

        if (isWindows()) {
            if (safeWrite(() -> WindowsCoreAudioVolume.writeScalar(cachedVolume))) {
                return;
            }
            if (safeWrite(() -> WindowsMixerVolume.writeScalar(cachedVolume))) {
                return;
            }
        }

        try {
            FloatControl control = resolveControl();
            if (control == null) {
                return;
            }
            control.setValue(scalarToControl(control, cachedVolume));
        } catch (Throwable ignored) {
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static float safeRead(FloatSupplier supplier) {
        try {
            return supplier.get();
        } catch (Throwable ignored) {
            return -1f;
        }
    }

    private static boolean safeWrite(BooleanSupplier supplier) {
        try {
            return supplier.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static FloatControl resolveControl() {
        FloatControl cached = masterControl;
        if (cached != null) {
            return cached;
        }
        synchronized (WindowsSystemVolume.class) {
            if (masterControl != null) {
                return masterControl;
            }
            masterControl = findMasterControl();
            return masterControl;
        }
    }

    private static FloatControl findMasterControl() {
        FloatControl control = findControlOnMixer(null);
        if (control != null) {
            return control;
        }
        javax.sound.sampled.Mixer.Info[] infos = javax.sound.sampled.AudioSystem.getMixerInfo();
        for (javax.sound.sampled.Mixer.Info info : infos) {
            control = findControlOnMixer(info);
            if (control != null) {
                return control;
            }
        }
        return null;
    }

    private static FloatControl findControlOnMixer(javax.sound.sampled.Mixer.Info info) {
        try {
            javax.sound.sampled.Mixer mixer = info == null
                    ? javax.sound.sampled.AudioSystem.getMixer(null)
                    : javax.sound.sampled.AudioSystem.getMixer(info);
            if (mixer == null) {
                return null;
            }
            if (mixer.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                return (FloatControl) mixer.getControl(FloatControl.Type.MASTER_GAIN);
            }
            if (mixer.isControlSupported(FloatControl.Type.VOLUME)) {
                return (FloatControl) mixer.getControl(FloatControl.Type.VOLUME);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static float scalarFromControl(FloatControl control) {
        float min = control.getMinimum();
        float max = control.getMaximum();
        if (max <= min) {
            return cachedVolume;
        }
        return clamp((control.getValue() - min) / (max - min));
    }

    private static float scalarToControl(FloatControl control, float scalar) {
        float min = control.getMinimum();
        float max = control.getMaximum();
        return min + clamp(scalar) * (max - min);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    @FunctionalInterface
    private interface FloatSupplier {
        float get();
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean get();
    }
}
