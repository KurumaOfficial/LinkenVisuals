package ru.method.linkenvisuals.screen.account;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/**
 * Рефлексивный мост к моду In-Game Account Switcher ({@code ru.vidtu.ias}). Пакеты IAS не
 * обфусцируются, поэтому рефлексия по реальным именам валидна. Всё резолвится один раз в
 * static-блоке; любой сбой → {@link #available()} == false, и наш экран просто не открывается
 * (грациозный отказ). Единственный источник правды по API — разведка {@code acct_recon_0.md}.
 *
 * <p>UI держит только плоский снимок {@link IasAccount}, а не живые объекты IAS.</p>
 */
public final class IasBridge {

    private IasBridge() {}

    /** Плоский снимок аккаунта IAS — UI не касается рефлексии. handle = живой Account для мутаций. */
    public record IasAccount(Object handle, UUID uuid, String name,
                             boolean canLogin, boolean insecure, UUID skin, String type) {}

    private static final boolean OK;

    // Поле-список и методы интерфейса Account.
    private static Field fAccounts;
    private static Method mUuid, mName, mCanLogin, mInsecure, mSkin, mType, mLogin;
    // Статические утилиты IAS.
    private static Method mSave, mDisclaimers, mExecutor, mIasAccount, mOfflineUuid;
    // Конструкторы.
    private static Constructor<?> cOffline, cLoginData, cLoginPopup, cAccountScreen;

    static {
        boolean ok = false;
        try {
            if (FabricLoader.getInstance().isModLoaded("ias")) {
                ClassLoader cl = IasBridge.class.getClassLoader();
                Class<?> storage = Class.forName("ru.vidtu.ias.config.IASStorage", true, cl);
                Class<?> account = Class.forName("ru.vidtu.ias.account.Account", true, cl);
                Class<?> offline = Class.forName("ru.vidtu.ias.account.OfflineAccount", true, cl);
                Class<?> ias = Class.forName("ru.vidtu.ias.IAS", true, cl);
                Class<?> iasMc = Class.forName("ru.vidtu.ias.IASMinecraft", true, cl);
                Class<?> loginData = Class.forName("ru.vidtu.ias.auth.LoginData", true, cl);
                Class<?> loginHandler = Class.forName("ru.vidtu.ias.auth.handlers.LoginHandler", true, cl);
                Class<?> loginPopup = Class.forName("ru.vidtu.ias.screen.LoginPopupScreen", true, cl);
                Class<?> accountScreen = Class.forName("ru.vidtu.ias.screen.AccountScreen", true, cl);

                fAccounts = storage.getField("ACCOUNTS");

                mUuid = account.getMethod("uuid");
                mName = account.getMethod("name");
                mCanLogin = account.getMethod("canLogin");
                mInsecure = account.getMethod("insecure");
                mSkin = account.getMethod("skin");
                mType = account.getMethod("type");
                try {
                    mLogin = account.getMethod("login", loginHandler, Runnable.class);
                } catch (NoSuchMethodException legacy) {
                    mLogin = account.getMethod("login", loginHandler);
                }

                mSave = ias.getMethod("saveStorage");
                mDisclaimers = ias.getMethod("disclaimersStorage");
                mExecutor = ias.getMethod("executor");
                // MinecraftClient-параметр берём по РЕАЛЬНОМУ рантайм-классу (в проде intermediary).
                mIasAccount = iasMc.getMethod("account", MinecraftClient.class, loginData);
                mOfflineUuid = offline.getMethod("uuid", String.class);

                cOffline = offline.getConstructor(String.class, UUID.class);
                cLoginData = loginData.getConstructor(String.class, UUID.class, String.class, boolean.class);
                cLoginPopup = loginPopup.getDeclaredConstructor(Screen.class); // package-private
                cLoginPopup.setAccessible(true);
                cAccountScreen = accountScreen.getConstructor(Screen.class);

                ok = true;
            }
        } catch (Throwable t) {
            ok = false;
        }
        OK = ok;
    }

    public static boolean available() {
        return OK;
    }

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    /** Логин запрещён в мире — IASMinecraft.account всё равно откажет (ias.error.world). */
    public static boolean inWorld() {
        MinecraftClient mc = mc();
        return mc != null && (mc.world != null || mc.player != null);
    }

    public static UUID currentUuid() {
        try {
            MinecraftClient mc = mc();
            return mc != null && mc.getSession() != null ? mc.getSession().getUuidOrNull() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static String currentName() {
        try {
            MinecraftClient mc = mc();
            return mc != null && mc.getSession() != null ? mc.getSession().getUsername() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Снимок списка аккаунтов IAS. Пустой список при любом сбое. */
    public static List<IasAccount> list() {
        List<IasAccount> out = new ArrayList<>();
        if (!OK) return out;
        try {
            List<?> raw = (List<?>) fAccounts.get(null);
            if (raw == null) return out;
            for (Object h : raw) {
                if (h == null) continue;
                try {
                    out.add(new IasAccount(h,
                            (UUID) mUuid.invoke(h),
                            (String) mName.invoke(h),
                            (boolean) mCanLogin.invoke(h),
                            (boolean) mInsecure.invoke(h),
                            (UUID) mSkin.invoke(h),
                            (String) mType.invoke(h)));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** Войти в выбранный аккаунт: offline — сами, Microsoft — переиспользуем LoginPopupScreen IAS. */
    public static void loginSelected(IasAccount a, Screen back) {
        if (!OK || a == null || inWorld()) return;
        if (!a.canLogin()) {
            loginOffline(a.name(), back);
            return;
        }
        try {
            MinecraftClient mc = mc();
            if (mc == null) return;
            // LoginPopupScreen сам решает рефреш токенов / запрос пароля / применение сессии / возврат.
            Object popup = cLoginPopup.newInstance(back);
            mc.setScreen((Screen) popup);
            Executor exec = (Executor) mExecutor.invoke(null);
            exec.execute(() -> {
                try {
                    if (mLogin.getParameterCount() == 1) {
                        mLogin.invoke(a.handle(), popup);
                    } else {
                        mLogin.invoke(a.handle(), popup, null);
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable t) {
            // Хрупкий package-private экран IAS сломался — уводим в родной экран IAS.
            openIasMicrosoftAdd(back);
        }
    }

    /** Войти оффлайн по нику: OfflineAccount.login() не работает, LoginData собираем вручную. */
    public static void loginOffline(String nick, Screen back) {
        if (!OK || nick == null || inWorld()) return;
        try {
            MinecraftClient mc = mc();
            if (mc == null) return;
            UUID id = (UUID) mOfflineUuid.invoke(null, nick);
            Object data = cLoginData.newInstance(nick, id, "ias:offline", false);
            Object future = mIasAccount.invoke(null, mc, data);
            if (future instanceof CompletableFuture<?> cf) {
                cf.thenRunAsync(() -> mc.setScreen(back), mc);
            } else {
                mc.execute(() -> mc.setScreen(back));
            }
        } catch (Throwable ignored) {
        }
    }

    /** Создать+сохранить offline-аккаунт. Валидация ника на нашей стороне. */
    public static boolean addOffline(String nick) {
        if (!OK || !validNick(nick)) return false;
        try {
            Object acc = cOffline.newInstance(nick, null);
            @SuppressWarnings("unchecked")
            List<Object> raw = (List<Object>) fAccounts.get(null);
            raw.removeIf(Predicate.isEqual(acc)); // дедуп по equals (у offline — по name)
            raw.add(acc);
            mDisclaimers.invoke(null);
            mSave.invoke(null);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Переименовать offline (для Microsoft не поддержано — кнопка «Изменить» дизейблится). */
    public static boolean renameOffline(IasAccount a, String nick) {
        if (!OK || a == null || a.canLogin() || !validNick(nick)) return false;
        try {
            @SuppressWarnings("unchecked")
            List<Object> raw = (List<Object>) fAccounts.get(null);
            raw.remove(a.handle());
            Object acc = cOffline.newInstance(nick, null);
            raw.removeIf(Predicate.isEqual(acc));
            raw.add(acc);
            mDisclaimers.invoke(null);
            mSave.invoke(null);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Удалить аккаунт + сохранить. */
    public static boolean remove(IasAccount a) {
        if (!OK || a == null) return false;
        try {
            @SuppressWarnings("unchecked")
            List<Object> raw = (List<Object>) fAccounts.get(null);
            raw.remove(a.handle());
            mDisclaimers.invoke(null);
            mSave.invoke(null);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Добавление НОВОГО Microsoft-аккаунта (OAuth) — делегат в родной экран IAS. Реимплементация
     * OAuth/пароля в нашем материале не окупается. ponytail: кратковременный уход в UI IAS —
     * потолок; заменить только при критичной визуальной цельности.
     */
    public static void openIasMicrosoftAdd(Screen parent) {
        if (!OK) return;
        try {
            MinecraftClient mc = mc();
            if (mc == null) return;
            mc.setScreen((Screen) cAccountScreen.newInstance(parent));
        } catch (Throwable ignored) {
        }
    }

    private static boolean validNick(String nick) {
        if (nick == null) return false;
        int n = nick.length();
        if (n < 3 || n > 16) return false;
        for (int i = 0; i < n; i++) {
            char c = nick.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '_';
            if (!ok) return false;
        }
        return true;
    }
}
