package ru.method.linkenvisuals.utils.mediaplayer;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import ru.method.linkenvisuals.LinkenVisuals;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * Windows master output volume via Core Audio {@code IAudioEndpointVolume}.
 */
final class WindowsCoreAudioVolume {
    private static final Guid.CLSID CLSID_MMDeviceEnumerator =
            new Guid.CLSID("BCDE0395-E52F-467C-8E3D-C4579291692E");
    private static final Guid.IID IID_IMMDeviceEnumerator =
            new Guid.IID("A95664D2-9614-4FCA-AFAC-3E6D667664EF");
    private static final Guid.IID IID_IAudioEndpointVolume =
            new Guid.IID("5CDF2C82-841E-4546-9722-0CF74078229A");

    private static final int E_DATAFLOW_RENDER = 0;
    private static final int E_ROLE_MULTIMEDIA = 1;
    private static final int CLSCTX_ALL = 0x17;

    private static volatile EndpointVolume endpointVolume;

    private WindowsCoreAudioVolume() {
    }

    static boolean isAvailable() {
        return ensureEndpointVolume() != null;
    }

    static float readScalar() {
        EndpointVolume volume = ensureEndpointVolume();
        if (volume == null) {
            return -1f;
        }
        FloatByReference scalar = new FloatByReference();
        WinNT.HRESULT hr = volume.GetMasterVolumeLevelScalar(scalar);
        if (failed(hr)) {
            invalidate();
            return -1f;
        }
        return clamp(scalar.getValue());
    }

    static boolean writeScalar(float scalar) {
        EndpointVolume volume = ensureEndpointVolume();
        if (volume == null) {
            LinkenVisuals.LOGGER.warn("[Volume] CoreAudio endpoint unavailable (CoCreate/Activate failed)");
            return false;
        }
        float target = clamp(scalar);
        WinNT.HRESULT hr = volume.SetMasterVolumeLevelScalar(target, null);
        if (failed(hr)) {
            LinkenVisuals.LOGGER.warn("[Volume] SetMasterVolumeLevelScalar failed hr=0x{}",
                    hr == null ? "null" : Integer.toHexString(hr.intValue()));
            invalidate();
            return false;
        }
        // Проверка записи чтением — если значение не применилось, считаем запись неуспешной.
        FloatByReference back = new FloatByReference();
        WinNT.HRESULT hr2 = volume.GetMasterVolumeLevelScalar(back);
        if (!failed(hr2) && Math.abs(back.getValue() - target) > 0.02f) {
            LinkenVisuals.LOGGER.warn("[Volume] write didn't take: target={} readback={}", target, back.getValue());
            return false;
        }
        return true;
    }

    private static EndpointVolume ensureEndpointVolume() {
        EndpointVolume cached = endpointVolume;
        if (cached != null) {
            return cached;
        }
        synchronized (WindowsCoreAudioVolume.class) {
            if (endpointVolume != null) {
                return endpointVolume;
            }
            endpointVolume = createEndpointVolume();
            return endpointVolume;
        }
    }

    private static EndpointVolume createEndpointVolume() {
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);
        PointerByReference enumeratorRef = new PointerByReference();
        WinNT.HRESULT hr = Ole32.INSTANCE.CoCreateInstance(
                CLSID_MMDeviceEnumerator,
                null,
                CLSCTX_ALL,
                IID_IMMDeviceEnumerator,
                enumeratorRef
        );
        if (failed(hr)) {
            return null;
        }

        DeviceEnumerator enumerator = new DeviceEnumerator(enumeratorRef.getValue());
        PointerByReference deviceRef = new PointerByReference();
        hr = enumerator.GetDefaultAudioEndpoint(E_DATAFLOW_RENDER, E_ROLE_MULTIMEDIA, deviceRef);
        enumerator.Release();
        if (failed(hr)) {
            LinkenVisuals.LOGGER.warn("[Volume] GetDefaultAudioEndpoint failed hr=0x{}",
                    hr == null ? "null" : Integer.toHexString(hr.intValue()));
            return null;
        }

        AudioDevice device = new AudioDevice(deviceRef.getValue());
        PointerByReference volumeRef = new PointerByReference();
        hr = device.Activate(IID_IAudioEndpointVolume, CLSCTX_ALL, null, volumeRef);
        device.Release();
        if (failed(hr)) {
            LinkenVisuals.LOGGER.warn("[Volume] IAudioEndpointVolume Activate failed hr=0x{}",
                    hr == null ? "null" : Integer.toHexString(hr.intValue()));
            return null;
        }

        return new EndpointVolume(volumeRef.getValue());
    }

    private static void invalidate() {
        synchronized (WindowsCoreAudioVolume.class) {
            EndpointVolume volume = endpointVolume;
            endpointVolume = null;
            if (volume != null) {
                volume.Release();
            }
        }
    }

    private static boolean failed(WinNT.HRESULT hr) {
        return hr == null || hr.intValue() < 0;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static final class DeviceEnumerator extends Unknown {
        DeviceEnumerator(Pointer pointer) {
            super(pointer);
        }

        WinNT.HRESULT GetDefaultAudioEndpoint(int dataFlow, int role, PointerByReference ppDevice) {
            return (WinNT.HRESULT) _invokeNativeObject(4, new Object[]{dataFlow, role, ppDevice}, WinNT.HRESULT.class);
        }
    }

    private static final class AudioDevice extends Unknown {
        AudioDevice(Pointer pointer) {
            super(pointer);
        }

        WinNT.HRESULT Activate(Guid.IID iid, int dwClsCtx, Pointer activationParams, PointerByReference ppInterface) {
            return (WinNT.HRESULT) _invokeNativeObject(3, new Object[]{iid, dwClsCtx, activationParams, ppInterface}, WinNT.HRESULT.class);
        }
    }

    private static final class EndpointVolume extends Unknown {
        EndpointVolume(Pointer pointer) {
            super(pointer);
        }

        WinNT.HRESULT SetMasterVolumeLevelScalar(float level, Guid.GUID eventContext) {
            return (WinNT.HRESULT) _invokeNativeObject(7, new Object[]{level, eventContext}, WinNT.HRESULT.class);
        }

        WinNT.HRESULT GetMasterVolumeLevelScalar(FloatByReference level) {
            return (WinNT.HRESULT) _invokeNativeObject(9, new Object[]{level}, WinNT.HRESULT.class);
        }
    }
}
