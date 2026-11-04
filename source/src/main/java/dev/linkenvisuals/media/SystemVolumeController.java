package dev.linkenvisuals.media;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.PointerByReference;
import dev.linkenvisuals.LinkenVisuals;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads and sets the Windows master output volume via Core Audio
 * ({@code IMMDeviceEnumerator} -> default render endpoint -> {@code IAudioEndpointVolume})
 * using raw JNA COM v-table calls.
 *
 * <p>All COM work runs on a dedicated single-thread executor that owns its own COM
 * apartment, so the render thread never touches COM. Any native failure flips the
 * controller to {@code unavailable} and the UI falls back to its internal model -
 * it never throws into the render loop.
 */
public final class SystemVolumeController {

    private static final String CLSID_MM_DEVICE_ENUMERATOR = "{BCDE0395-E52F-467C-8E3D-C4579291692E}";
    private static final String IID_IMM_DEVICE_ENUMERATOR = "{A95664D2-9614-4F35-A746-DE8DB63617E6}";
    private static final String IID_IAUDIO_ENDPOINT_VOLUME = "{5CDF2C82-841E-4546-9722-0CF74078229A}";

    private static final int CLSCTX_ALL = 0x17;
    private static final int COINIT_MULTITHREADED = 0x0;
    private static final int E_RENDER = 0;
    private static final int E_CONSOLE = 0;

    // IAudioEndpointVolume v-table slots (IUnknown occupies 0..2).
    private static final int VT_SET_MASTER_SCALAR = 7;
    private static final int VT_GET_MASTER_SCALAR = 9;
    private static final int VT_GET_DEFAULT_ENDPOINT = 4;
    private static final int VT_ACTIVATE = 3;

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LV-Volume");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicReference<Float> volume = new AtomicReference<>(null);
    private static volatile boolean initialized;
    private static volatile boolean unavailable;
    private static Pointer endpointVolume;

    private SystemVolumeController() {
    }

    /** Kick off COM init + initial volume read (idempotent, non-blocking). */
    public static void ensureInit() {
        if (initialized || unavailable || !isWindows()) {
            if (!isWindows()) {
                unavailable = true;
            }
            return;
        }
        EXEC.submit(() -> {
            if (!initialized && !unavailable) {
                initOnComThread();
            }
        });
    }

    /** Last known master volume in [0,1], or {@code null} if not yet read / unavailable. */
    public static Float currentVolume() {
        return volume.get();
    }

    public static boolean isUnavailable() {
        return unavailable;
    }

    /** Request a master-volume change (optimistically updates the cached value immediately). */
    public static void setVolume(float target) {
        if (unavailable || !isWindows()) {
            return;
        }
        float clamped = clamp01(target);
        volume.set(clamped);
        EXEC.submit(() -> {
            if (!initialized) {
                initOnComThread();
            }
            setVolumeInternal(clamped);
        });
    }

    /** Refresh the cached value from the system (e.g. when the GUI opens). */
    public static void refresh() {
        if (unavailable || !isWindows()) {
            return;
        }
        EXEC.submit(() -> {
            if (!initialized) {
                initOnComThread();
            }
            readVolumeInternal();
        });
    }

    // ---- COM-thread internals --------------------------------------------

    private static void initOnComThread() {
        try {
            Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, COINIT_MULTITHREADED);

            Guid.GUID clsid = new Guid.GUID(CLSID_MM_DEVICE_ENUMERATOR);
            Guid.GUID iidEnum = new Guid.GUID(IID_IMM_DEVICE_ENUMERATOR);
            PointerByReference ppEnum = new PointerByReference();
            WinNT.HRESULT hr = Ole32.INSTANCE.CoCreateInstance(clsid, Pointer.NULL, CLSCTX_ALL, iidEnum, ppEnum);
            if (failed(hr) || ppEnum.getValue() == null) {
                markUnavailable("CoCreateInstance(MMDeviceEnumerator)", hr == null ? 0 : hr.intValue());
                return;
            }
            Pointer enumerator = ppEnum.getValue();

            PointerByReference ppDevice = new PointerByReference();
            int hrEndpoint = invoke(enumerator, VT_GET_DEFAULT_ENDPOINT, E_RENDER, E_CONSOLE, ppDevice);
            if (hrEndpoint < 0 || ppDevice.getValue() == null) {
                release(enumerator);
                markUnavailable("GetDefaultAudioEndpoint", hrEndpoint);
                return;
            }
            Pointer device = ppDevice.getValue();

            Guid.GUID iidVol = new Guid.GUID(IID_IAUDIO_ENDPOINT_VOLUME);
            iidVol.write();
            PointerByReference ppVol = new PointerByReference();
            int hrActivate = invoke(device, VT_ACTIVATE, iidVol.getPointer(), CLSCTX_ALL, Pointer.NULL, ppVol);
            if (hrActivate < 0 || ppVol.getValue() == null) {
                release(device);
                release(enumerator);
                markUnavailable("IMMDevice::Activate(IAudioEndpointVolume)", hrActivate);
                return;
            }

            endpointVolume = ppVol.getValue();
            release(device);
            release(enumerator);
            initialized = true;
            readVolumeInternal();
        } catch (Throwable t) {
            unavailable = true;
            LinkenVisuals.LOGGER.debug("[Volume] Core Audio init failed", t);
        }
    }

    private static void setVolumeInternal(float level) {
        if (!initialized || endpointVolume == null) {
            return;
        }
        try {
            int hr = invoke(endpointVolume, VT_SET_MASTER_SCALAR, Float.valueOf(clamp01(level)), Pointer.NULL);
            if (hr >= 0) {
                volume.set(clamp01(level));
            }
        } catch (Throwable t) {
            LinkenVisuals.LOGGER.debug("[Volume] SetMasterVolumeLevelScalar failed", t);
        }
    }

    private static void readVolumeInternal() {
        if (!initialized || endpointVolume == null) {
            return;
        }
        try {
            Memory out = new Memory(4);
            int hr = invoke(endpointVolume, VT_GET_MASTER_SCALAR, out);
            if (hr >= 0) {
                volume.set(clamp01(out.getFloat(0)));
            }
        } catch (Throwable t) {
            LinkenVisuals.LOGGER.debug("[Volume] GetMasterVolumeLevelScalar failed", t);
        }
    }

    private static int invoke(Pointer comObject, int vtableIndex, Object... args) {
        Pointer vtable = comObject.getPointer(0);
        Pointer fnPtr = vtable.getPointer((long) vtableIndex * Native.POINTER_SIZE);
        Function fn = Function.getFunction(fnPtr);
        Object[] all = new Object[args.length + 1];
        all[0] = comObject;
        System.arraycopy(args, 0, all, 1, args.length);
        return fn.invokeInt(all);
    }

    private static void release(Pointer comObject) {
        if (comObject == null) {
            return;
        }
        try {
            invoke(comObject, 2); // IUnknown::Release
        } catch (Throwable ignored) {
        }
    }

    private static void markUnavailable(String stage, int hr) {
        unavailable = true;
        LinkenVisuals.LOGGER.debug("[Volume] {} failed: 0x{}", stage, Integer.toHexString(hr));
    }

    private static boolean failed(WinNT.HRESULT hr) {
        return hr == null || hr.intValue() < 0;
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
