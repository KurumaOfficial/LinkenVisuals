package dev.linkenvisuals.media;

import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinError;

/**
 * Initializes WinRT/COM on worker threads used by {@link SystemMediaController}.
 */
public final class WinRtInterop {

    private static final int RO_INIT_MULTITHREADED = 1;
    private static final int COINIT_MULTITHREADED = 0;

    private enum InitState {
        NOT_TRIED,
        OK,
        FAILED
    }

    private enum InitKind {
        NONE,
        RO,
        CO
    }

    private static final ThreadLocal<InitState> THREAD_STATE = ThreadLocal.withInitial(() -> InitState.NOT_TRIED);
    private static final ThreadLocal<InitKind> THREAD_KIND = ThreadLocal.withInitial(() -> InitKind.NONE);

    private WinRtInterop() {
    }

    public static boolean ensureInitialized() {
        if (!isWindows()) {
            return false;
        }

        InitState state = THREAD_STATE.get();
        if (state == InitState.OK) {
            return true;
        }
        if (state == InitState.FAILED) {
            return false;
        }

        InitAttempt attempt = tryRoInitialize();
        if (!attempt.ok) {
            attempt = tryCoInitializeEx();
        }

        THREAD_STATE.set(attempt.ok ? InitState.OK : InitState.FAILED);
        THREAD_KIND.set(attempt.kind);
        return attempt.ok;
    }

    public static void uninitializeCurrentThread() {
        InitKind kind = THREAD_KIND.get();
        if (kind == InitKind.NONE) {
            return;
        }

        try {
            if (kind == InitKind.RO) {
                NativeLibraryHelper.invokeVoid("combase", "RoUninitialize");
            } else if (kind == InitKind.CO) {
                Ole32.INSTANCE.CoUninitialize();
            }
        } catch (Throwable ignored) {
        } finally {
            THREAD_KIND.set(InitKind.NONE);
            THREAD_STATE.set(InitState.NOT_TRIED);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private record InitAttempt(boolean ok, InitKind kind) {
    }

    private static InitAttempt tryRoInitialize() {
        try {
            int hr = NativeLibraryHelper.invokeInt("combase", "RoInitialize", RO_INIT_MULTITHREADED);
            if (hr == WinError.RPC_E_CHANGED_MODE) {
                return new InitAttempt(true, InitKind.NONE);
            }
            return new InitAttempt(isHresultOk(hr), InitKind.RO);
        } catch (Throwable ignored) {
            return new InitAttempt(false, InitKind.NONE);
        }
    }

    private static InitAttempt tryCoInitializeEx() {
        try {
            WinNT.HRESULT hr = Ole32.INSTANCE.CoInitializeEx(null, COINIT_MULTITHREADED);
            if (hr.intValue() == WinError.RPC_E_CHANGED_MODE) {
                return new InitAttempt(true, InitKind.NONE);
            }
            return new InitAttempt(isHresultOk(hr.intValue()), InitKind.CO);
        } catch (Throwable ignored) {
            return new InitAttempt(false, InitKind.NONE);
        }
    }

    private static boolean isHresultOk(int hr) {
        return hr >= 0 || hr == WinError.RPC_E_CHANGED_MODE;
    }

    private static final class NativeLibraryHelper {
        private NativeLibraryHelper() {
        }

        private static int invokeInt(String libraryName, String functionName, int argument) {
            com.sun.jna.Function function = com.sun.jna.NativeLibrary.getInstance(libraryName).getFunction(functionName);
            return function.invokeInt(new Object[]{argument});
        }

        private static void invokeVoid(String libraryName, String functionName) {
            com.sun.jna.Function function = com.sun.jna.NativeLibrary.getInstance(libraryName).getFunction(functionName);
            function.invokeVoid(new Object[0]);
        }
    }
}
