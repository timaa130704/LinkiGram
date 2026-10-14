package app.nimarkogram.messenger.appearance;

import app.nimarkogram.messenger.NimarkoConfig;

/**
 * Совместимый слой настроек для компонентов, портированных из exteraless
 * (шторка-меню, заголовок шторки, менеджер пунктов меню). Все значения
 * хранятся в {@link NimarkoConfig}.
 *
 * exteraless хранит их в своём {@code app.exteraless.appearance.AppearanceConfig}
 * (ConfigItem-обёртки); здесь оставлен тот же небольшой API, чтобы портированный
 * код не пришлось переписывать целиком.
 */
public final class AppearanceConfig {

    private AppearanceConfig() {
    }

    public static void ensureLoaded() {
    }

    public static boolean navigationDrawer() {
        return NimarkoConfig.navigationDrawer;
    }

    public static void setNavigationDrawer(boolean value) {
        NimarkoConfig.setNavigationDrawer(value);
    }

    public static boolean immersiveDrawerAnimation() {
        return NimarkoConfig.immersiveDrawerAnimation;
    }

    public static void setImmersiveDrawerAnimation(boolean value) {
        NimarkoConfig.setImmersiveDrawerAnimation(value);
    }

    public static int getAvatarCorners(float size) {
        return com.exteragram.messenger.ExteraConfig.getAvatarCorners(size);
    }

    /** Обёртка над строковым значением раскладки главного меню. */
    public static final StringItem mainMenuLayout = new StringItem();

    public static final class StringItem {
        public String String() {
            return NimarkoConfig.mainMenuLayout;
        }

        public void setConfigString(String value) {
            NimarkoConfig.setMainMenuLayout(value);
        }
    }
}