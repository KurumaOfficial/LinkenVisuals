package ru.method.linkenvisuals.utils.mediaplayer;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * System master volume through WinMM mixer API (reliable on Windows 10/11).
 */
final class WindowsMixerVolume {
    private static final int MMSYSERR_NOERROR = 0;
    private static final int MIXERCONTROL_CONTROLTYPE_VOLUME = 0x50030001;
    private static final int MIXERLINE_COMPONENTTYPE_DST_SPEAKERS = 0x00000004;
    private static final int MIXER_GETLINEINFOF_COMPONENTTYPE = 0x00000003;
    private static final int MIXER_GETLINECONTROLSF_ONEBYTYPE = 0x00000002;
    private static final int MIXER_GETCONTROLDETAILSF_VALUE = 0x00000000;
    private static final int MIXER_SETCONTROLDETAILSF_VALUE = 0x00000000;

    private static volatile Integer cachedHandle;
    private static volatile int cachedControlId = -1;
    private static volatile int cachedMin;
    private static volatile int cachedMax;

    private WindowsMixerVolume() {
    }

    static boolean isAvailable() {
        return resolveControl() != null;
    }

    static float readScalar() {
        ControlHandle handle = resolveControl();
        if (handle == null) {
            return -1f;
        }
        Memory valueMemory = new Memory(4);
        MIXERCONTROLDETAILS details = new MIXERCONTROLDETAILS();
        details.cbStruct = details.size();
        details.dwControlID = handle.controlId();
        details.cChannels = 1;
        details.cMultipleItems = 0;
        details.cbDetails = 4;
        details.paDetails = valueMemory;

        if (WinMM.INSTANCE.mixerGetControlDetails(handle.mixer(), details, MIXER_GETCONTROLDETAILSF_VALUE) != MMSYSERR_NOERROR) {
            invalidate();
            return -1f;
        }
        int raw = valueMemory.getInt(0);
        return scalarFromRaw(raw, handle.min(), handle.max());
    }

    static boolean writeScalar(float scalar) {
        ControlHandle handle = resolveControl();
        if (handle == null) {
            return false;
        }
        int raw = rawFromScalar(scalar, handle.min(), handle.max());
        Memory valueMemory = new Memory(4);
        valueMemory.setInt(0, raw);

        MIXERCONTROLDETAILS details = new MIXERCONTROLDETAILS();
        details.cbStruct = details.size();
        details.dwControlID = handle.controlId();
        details.cChannels = 1;
        details.cMultipleItems = 0;
        details.cbDetails = 4;
        details.paDetails = valueMemory;

        if (WinMM.INSTANCE.mixerSetControlDetails(handle.mixer(), details, MIXER_SETCONTROLDETAILSF_VALUE) != MMSYSERR_NOERROR) {
            invalidate();
            return false;
        }
        return true;
    }

    private static ControlHandle resolveControl() {
        if (cachedControlId >= 0 && cachedHandle != null) {
            return new ControlHandle(cachedHandle, cachedControlId, cachedMin, cachedMax);
        }
        synchronized (WindowsMixerVolume.class) {
            if (cachedControlId >= 0 && cachedHandle != null) {
                return new ControlHandle(cachedHandle, cachedControlId, cachedMin, cachedMax);
            }
            return discoverControl();
        }
    }

    private static ControlHandle discoverControl() {
        IntByReference mixerRef = new IntByReference();
        if (WinMM.INSTANCE.mixerOpen(mixerRef, 0, null, null, 0) != MMSYSERR_NOERROR) {
            return null;
        }
        int mixer = mixerRef.getValue();
        try {
            MIXERLINE line = new MIXERLINE();
            line.cbStruct = line.size();
            line.dwComponentType = MIXERLINE_COMPONENTTYPE_DST_SPEAKERS;
            if (WinMM.INSTANCE.mixerGetLineInfo(mixer, line, MIXER_GETLINEINFOF_COMPONENTTYPE) != MMSYSERR_NOERROR) {
                WinMM.INSTANCE.mixerClose(mixer);
                return null;
            }

            Memory controlMemory = new Memory(new MIXERCONTROL().size());
            MIXERLINECONTROLS lineControls = new MIXERLINECONTROLS();
            lineControls.cbStruct = lineControls.size();
            lineControls.dwLineID = line.dwLineID;
            lineControls.dwControlID = MIXERCONTROL_CONTROLTYPE_VOLUME;
            lineControls.cControls = 1;
            lineControls.cbmxctrl = (int) controlMemory.size();
            lineControls.pamxctrl = controlMemory;

            if (WinMM.INSTANCE.mixerGetLineControls(mixer, lineControls, MIXER_GETLINECONTROLSF_ONEBYTYPE) != MMSYSERR_NOERROR) {
                WinMM.INSTANCE.mixerClose(mixer);
                return null;
            }

            MIXERCONTROL control = new MIXERCONTROL(controlMemory);
            control.read();
            cachedHandle = mixer;
            cachedControlId = control.dwControlID;
            cachedMin = control.bounds.lMinimum;
            cachedMax = control.bounds.lMaximum;
            return new ControlHandle(mixer, cachedControlId, cachedMin, cachedMax);
        } catch (RuntimeException ex) {
            WinMM.INSTANCE.mixerClose(mixer);
            return null;
        }
    }

    private static void invalidate() {
        synchronized (WindowsMixerVolume.class) {
            if (cachedHandle != null) {
                WinMM.INSTANCE.mixerClose(cachedHandle);
            }
            cachedHandle = null;
            cachedControlId = -1;
        }
    }

    private static float scalarFromRaw(int raw, int min, int max) {
        if (max <= min) {
            return 0f;
        }
        return clamp((raw - min) / (float) (max - min));
    }

    private static int rawFromScalar(float scalar, int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + Math.round(clamp(scalar) * (max - min));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private record ControlHandle(int mixer, int controlId, int min, int max) {
    }

    private interface WinMM extends StdCallLibrary {
        WinMM INSTANCE = Native.load("winmm", WinMM.class, W32APIOptions.DEFAULT_OPTIONS);

        int mixerOpen(IntByReference phmx, int uMxId, Pointer dwCallback, Pointer dwInstance, int fdwOpen);

        int mixerClose(int hmx);

        int mixerGetLineInfo(int hmx, MIXERLINE pmxl, int fdwInfo);

        int mixerGetLineControls(int hmx, MIXERLINECONTROLS pmxlc, int fdwControls);

        int mixerGetControlDetails(int hmx, MIXERCONTROLDETAILS pmxcd, int fdwDetails);

        int mixerSetControlDetails(int hmx, MIXERCONTROLDETAILS pmxcd, int fdwDetails);
    }

    @Structure.FieldOrder({
            "cbStruct", "dwDestination", "dwSource", "dwLineID", "fdwLine", "dwUser",
            "dwComponentType", "cChannels", "cConnections", "cControls",
            "szShortName", "szName", "dwType", "dwDeviceID", "wMid", "wPid", "vDriverVersion", "szPname"
    })
    public static class MIXERLINE extends Structure {
        public int cbStruct;
        public int dwDestination;
        public int dwSource;
        public int dwLineID;
        public int fdwLine;
        public Pointer dwUser;
        public int dwComponentType;
        public int cChannels;
        public int cConnections;
        public int cControls;
        public byte[] szShortName = new byte[16];
        public byte[] szName = new byte[64];
        public int dwType;
        public int dwDeviceID;
        public short wMid;
        public short wPid;
        public int vDriverVersion;
        public byte[] szPname = new byte[32];

        MIXERLINE() {
        }
    }

    @Structure.FieldOrder({"cbStruct", "dwLineID", "dwControlID", "cControls", "cbmxctrl", "pamxctrl"})
    public static class MIXERLINECONTROLS extends Structure {
        public int cbStruct;
        public int dwLineID;
        public int dwControlID;
        public int cControls;
        public int cbmxctrl;
        public Pointer pamxctrl;

        MIXERLINECONTROLS() {
        }
    }

    @Structure.FieldOrder({
            "cbStruct", "dwControlID", "dwControlType", "fdwControl", "cMultipleItems",
            "szShortName", "szName", "bounds", "metrics"
    })
    public static class MIXERCONTROL extends Structure {
        public int cbStruct;
        public int dwControlID;
        public int dwControlType;
        public int fdwControl;
        public int cMultipleItems;
        public byte[] szShortName = new byte[16];
        public byte[] szName = new byte[64];
        public BoundsUnion bounds = new BoundsUnion();
        public MetricsUnion metrics = new MetricsUnion();

        MIXERCONTROL() {
        }

        MIXERCONTROL(Pointer pointer) {
            super(pointer);
        }

        @Structure.FieldOrder({"lMinimum", "lMaximum"})
        public static class BoundsUnion extends Structure {
            public int lMinimum;
            public int lMaximum;
        }

        @Structure.FieldOrder({"cSteps", "cbCustomData", "fdwReserved"})
        public static class MetricsUnion extends Structure {
            public int cSteps;
            public int cbCustomData;
            public int fdwReserved;
        }
    }

    @Structure.FieldOrder({"cbStruct", "dwControlID", "cChannels", "cMultipleItems", "cbDetails", "paDetails"})
    public static class MIXERCONTROLDETAILS extends Structure {
        public int cbStruct;
        public int dwControlID;
        public int cChannels;
        public int cMultipleItems;
        public int cbDetails;
        public Pointer paDetails;

        MIXERCONTROLDETAILS() {
        }
    }
}
