package app.nimarkogram.messenger.xcfg;

/*
 * X-CFG — встроенный в LinkiGram способ обхода.
 * Порт плагина X-CFG для exteraGram (автор @x_vare, GPL), портирован с его
 * явного разрешения владельца форка. Все тексты UI — дословно из плагина.
 */
import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

public final class XcfgPrefs {

    public static final String API = "https://x-vpn.x-vare.com";
    public static final String BOT = "xvpnoff_bot";
    // UA оставлен как у плагина: сервер X-VPN ориентируется на него
    public static final String UA = "XCFG-ExteraPlugin/1.0";
    public static final String XCFG_PACKAGE = "com.example.x_cfg";
    public static final String XCFG_ACTIVITY = "com.example.x_cfg.MainActivity";
    public static final String TAB_TAG = "xcfg_tab";
    public static final int LOGIN_TIMEOUT_S = 45;
    public static final String PLUGIN_VERSION = "1.0.6";

    // Ядро Xray для «VPN в Telegram»: AndroidLibXrayLite (Lib v38, Xray-core v26.7.11)
    public static final String CORE_BASE = API + "/app/plugin/";
    public static final String CORE_DEX = "xcfgcore-v38.dex";
    public static final String CORE_DEX_SHA256 = "7de2aafb62527580ac750270147b4206dccb11be24e140749d1be689cbe3d27d";
    public static final String CORE_SO_GZ = "xcfgcore-v38-arm64.so.gz";
    public static final String CORE_SO_GZ_SHA256 = "ca84c8b59b4cb33b3ea64c127a417898dd987b2e3a04b91c4ed37ab2cb198fa2";
    public static final String CORE_SO_SHA256 = "bfab57a4d46437f8de7c01fb888d8fced8dfcf66aa6e4c42a94dd6b00525b891";

    public static final int EMERALD = 0xFF2FA84F;
    public static final int EMERALD_DARK = 0xFF26903A;
    public static final int WHITE = 0xFFFFFFFF;

    private XcfgPrefs() {
    }

    static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("xcfg_config", Context.MODE_PRIVATE);
    }

    public static String get(String key, String def) {
        try {
            return prefs().getString(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static void set(String key, String value) {
        try {
            prefs().edit().putString(key, value).apply();
        } catch (Exception ignore) {
        }
    }

    public static boolean getBool(String key, boolean def) {
        try {
            return prefs().getBoolean(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static void setBool(String key, boolean value) {
        try {
            prefs().edit().putBoolean(key, value).apply();
        } catch (Exception ignore) {
        }
    }

    public static int getInt(String key, int def) {
        try {
            return prefs().getInt(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static void setInt(String key, int value) {
        try {
            prefs().edit().putInt(key, value).apply();
        } catch (Exception ignore) {
        }
    }

    public static int uid() {
        try {
            return (int) UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        } catch (Exception e) {
            return 0;
        }
    }

    public static String token() {
        return get("token_" + uid(), "");
    }

    public static void setToken(String token) {
        set("token_" + uid(), token == null ? "" : token);
    }
}
