package app.nimarkogram.messenger.preferences;

import android.os.Build;
import android.content.Context;
import android.text.Html;
import android.text.SpannableString;
import android.util.TypedValue;
import android.view.View;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.nimarkogram.messenger.plugins.PluginsController;
import app.nimarkogram.messenger.plugins.ui.PluginsActivity;
import app.nimarkogram.messenger.utils.AppRestartHelper;
import app.nimarkogram.messenger.utils.text.LocaleUtils;
import app.nimarkogram.messenger.wsbypass.preferences.WsBypassPreferencesActivity;

import java.util.ArrayList;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.SettingsActivity;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.messenger.AndroidUtilities;

public class MainPreferencesActivity extends BasePreferencesActivity {

    public static final int ID_GENERAL      = 1;
    public static final int ID_APPEARANCE   = 2;
    public static final int ID_CHATS        = 3;
    public static final int ID_CAMERA       = 4;
    public static final int ID_PRIVACY      = 6;

    public static final int ID_RESTART      = 10;

    public static final int ID_PLUGINS        = 20;
    public static final int ID_DEBUG          = 21;
    private static final int ID_EXPERIMENTAL   = 22;
    public static final int ID_NIMARKO_MEDIA  = 23;
    private static final int ID_CHERRYGRAM_FORK = 24;
    public static final int ID_BANNERS        = 25;
    public static final int ID_UPDATES        = 26;
    public static final int ID_WSBYPASS       = 27;
    public static final int ID_TEXTANIM       = 28;
    public static final int ID_PILLSTACK      = 29;
    public static final int ID_SOURCE_CODE    = 30;
    public static final int ID_EASTER         = 31;
    public static final int ID_AYUMOMENTS     = 32;

    private int easterEggClicks = 0;
    private long easterEggLastClickTime = 0;

    private static final String SOURCE_REPOSITORY_URL = "https://github.com/timaa130704/LinkiGram";

    private UItem category(int id, IconBackgroundColors colors, int icon, int title, String alias) {
        return SettingsActivity.SettingCell.Factory.of(
                        id, colors.top, colors.bottom, icon,
                        LocaleController.getString(title))
                .setSearchable(this)
                .setLinkAlias(alias, this);
    }

    @Override
    public void fillItems(ArrayList<UItem> arrayList, UniversalAdapter universalAdapter) {
        // UItem.asCustom accepts height in dp and converts it inside UniversalAdapter.
        arrayList.add(UItem.asCustom(createAboutHeader(getContext()), 190));
        arrayList.add(UItem.asHeader(LocaleController.getString(R.string.AP_Header_General)));
        arrayList.add(category(ID_GENERAL, IconBackgroundColors.BLUE,
                R.drawable.msg_settings_solar, R.string.NM_Cat_General, "nimarko_general"));
        arrayList.add(category(ID_APPEARANCE, IconBackgroundColors.PURPLE,
                R.drawable.msg_theme_solar, R.string.NM_Cat_Appearance, "nimarko_appearance"));
        arrayList.add(category(ID_CHATS, IconBackgroundColors.BLUE_DEEP,
                R.drawable.msg_msgbubble3_solar, R.string.NM_Cat_Chats, "nimarko_chats"));
        arrayList.add(category(ID_CAMERA, IconBackgroundColors.CYAN,
                R.drawable.camera_solar, R.string.NM_Cat_Camera, "nimarko_camera"));
        
        arrayList.add(category(ID_PRIVACY, IconBackgroundColors.GREEN,
                R.drawable.msg_secret_solar, R.string.NM_Cat_Privacy, "nimarko_privacy"));
        arrayList.add(category(ID_AYUMOMENTS, IconBackgroundColors.RED,
                R.drawable.msg_delete_solar, R.string.NM_AyuMoments, "nimarko_ayumoments"));
        arrayList.add(UItem.asShadow(null));

        arrayList.add(UItem.asHeader(LocaleController.getString(R.string.NM_HUB_Header_Misc)));
        arrayList.add(UItem.asButton(2002, R.drawable.msg_customize, LocaleController.getString(R.string.LF_Menu)));
        if (PluginsController.isPluginEngineSupported()) {
            arrayList.add(category(ID_PLUGINS, IconBackgroundColors.PURPLE,
                    R.drawable.msg_plugins, R.string.Plugins, "nimarko_plugins"));
        }
        arrayList.add(category(ID_NIMARKO_MEDIA, IconBackgroundColors.BLUE_DEEP,
                R.drawable.msg_download_solar, R.string.NM_DownloadMedia, "nimarko_media"));
        arrayList.add(category(ID_BANNERS, IconBackgroundColors.ORANGE,
                R.drawable.msg_photos_solar, R.string.NM_BAN_Title, "nimarko_banners"));
        arrayList.add(category(ID_WSBYPASS, IconBackgroundColors.GREEN,
                R.drawable.msg_secret_solar, R.string.NM_WSB_Title, "nimarko_wsbypass"));
        arrayList.add(category(ID_TEXTANIM, IconBackgroundColors.PURPLE,
                R.drawable.msg_edit_solar, R.string.NM_TA_Title, "nimarko_textanim"));
        arrayList.add(category(ID_PILLSTACK, IconBackgroundColors.CYAN,
                R.drawable.msg_search_solar, R.string.NM_CARDS_Title, "nimarko_infocards"));
        arrayList.add(UItem.asShadow(null));

        arrayList.add(UItem.asHeader(LocaleController.getString(R.string.NM_HUB_Header_Advanced)));
        arrayList.add(category(ID_DEBUG, IconBackgroundColors.GRAY,
                R.drawable.msg_info_solar, R.string.NM_HUB_Debug, "nimarko_debug"));
        arrayList.add(category(ID_SOURCE_CODE, IconBackgroundColors.BLUE_DEEP,
                R.drawable.msg_link_2_solar, R.string.NM_HUB_SourceCode, "nimarko_source_code"));
        arrayList.add(UItem.asShadow(null));

        arrayList.add(UItem.asHeader(LocaleController.getString(R.string.NM_HUB_Header_Other)));
        arrayList.add(category(ID_RESTART, IconBackgroundColors.ORANGE_DEEP,
                R.drawable.msg_retry_solar, R.string.NM_HUB_Restart, "nimarko_restart"));
        arrayList.add(category(ID_UPDATES, IconBackgroundColors.BLUE,
                R.drawable.msg_info_solar, R.string.UP_CheckForUpdates, "nimarko_updates"));
        arrayList.add(UItem.asShadow(null));

        String forkedSubRaw = LocaleController.getString(R.string.NM_HUB_ForkedFromCherrygramSub);
        arrayList.add(UItem.asButton(ID_EASTER, forkedSubRaw));

    }

    private View createAboutHeader(Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        ImageView icon = new ImageView(context);
        // Draw the plate and plane separately so the logo follows the same
        // palette as the settings badges, including live theme changes.
        final android.graphics.Bitmap source = android.graphics.BitmapFactory.decodeResource(context.getResources(), R.drawable.linkigram_about_icon);
        final int width = source.getWidth(), height = source.getHeight();
        final int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        // Remove only the flat background connected to the image border.
        // The cat artwork inside the LinkiGram mark remains unchanged.
        final int background = pixels[0];
        final int[] queue = new int[pixels.length];
        int tail = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x != 0 && y != 0 && x != width - 1 && y != height - 1) continue;
                int index = y * width + x;
                if (pixels[index] == background) { pixels[index] = 0; queue[tail++] = index; }
            }
        }
        for (int head = 0; head < tail; head++) {
            int index = queue[head];
            int x = index % width;
            if (x > 0 && pixels[index - 1] == background) { pixels[index - 1] = 0; queue[tail++] = index - 1; }
            if (x + 1 < width && pixels[index + 1] == background) { pixels[index + 1] = 0; queue[tail++] = index + 1; }
            if (index >= width && pixels[index - width] == background) { pixels[index - width] = 0; queue[tail++] = index - width; }
            if (index + width < pixels.length && pixels[index + width] == background) { pixels[index + width] = 0; queue[tail++] = index + width; }
        }
        final android.graphics.Bitmap artwork = android.graphics.Bitmap.createBitmap(pixels, width, height, android.graphics.Bitmap.Config.ARGB_8888);
        final android.graphics.drawable.Drawable plane = new android.graphics.drawable.BitmapDrawable(context.getResources(), artwork);
        icon.setImageDrawable(new android.graphics.drawable.Drawable() {
            private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

            @Override
            public void draw(android.graphics.Canvas canvas) {
                final boolean dark = Theme.isCurrentThemeDark();
                final int mode = app.nimarkogram.messenger.appearance.SettingsIconColors.mode();
                int plate = dark ? 0xFF2E2E33 : 0xFFE6E6EB;
                int glyph = dark ? 0xFFF2F2F7 : 0xFF17171A;
                if (mode == app.nimarkogram.messenger.appearance.SettingsIconColors.ACCENT
                        || mode == app.nimarkogram.messenger.appearance.SettingsIconColors.CUSTOM) {
                    glyph = mode == app.nimarkogram.messenger.appearance.SettingsIconColors.CUSTOM
                            ? app.nimarkogram.messenger.appearance.SettingsIconColors.customColor()
                            : Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
                    plate = androidx.core.graphics.ColorUtils.blendARGB(
                            Theme.getColor(Theme.key_windowBackgroundWhite), glyph, dark ? 0.22f : 0.12f);
                } else if (mode == app.nimarkogram.messenger.appearance.SettingsIconColors.ORIGINAL) {
                    plate = 0xFF424242;
                    glyph = android.graphics.Color.WHITE;
                }
                android.graphics.Rect bounds = getBounds();
                float cx = bounds.exactCenterX();
                float cy = bounds.exactCenterY();
                float radius = Math.min(bounds.width(), bounds.height()) / 2f;
                paint.setStyle(android.graphics.Paint.Style.FILL);
                paint.setColor(plate);
                canvas.drawCircle(cx, cy, radius, paint);
                if (dark && mode == app.nimarkogram.messenger.appearance.SettingsIconColors.NEUTRAL) {
                    paint.setStyle(android.graphics.Paint.Style.STROKE);
                    paint.setStrokeWidth(AndroidUtilities.dp(1));
                    paint.setColor(0x1FFFFFFF);
                    canvas.drawCircle(cx, cy, radius - AndroidUtilities.dp(0.5f), paint);
                }
                plane.setBounds(bounds);
                plane.draw(canvas);
            }

            @Override public void setAlpha(int alpha) { }
            @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        });
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        icon.setClipToOutline(true);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(88), AndroidUtilities.dp(88));
        iconParams.bottomMargin = AndroidUtilities.dp(14);
        header.addView(icon, iconParams);

        TextView name = new TextView(context);
        name.setText("LinkiGram");
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
        name.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        name.setGravity(Gravity.CENTER);
        header.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView version = new TextView(context);
        String versionText = org.telegram.messenger.BuildVars.BUILD_VERSION_STRING;
        try {
            android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            int buildCode = info.versionCode / 10;
            versionText += " (" + buildCode + ")";
        } catch (Exception ignore) {
            // Keep the configured version visible if package metadata is unavailable.
        }
        version.setText(versionText);
        version.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams versionParams = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        versionParams.topMargin = AndroidUtilities.dp(6);
        header.addView(version, versionParams);

        return header;
    }

    @Override
    public String getTitle() {
        return LocaleController.getString(R.string.NimarkoGramSettings);
    }

    @Override
    public void onClick(UItem uItem, View view, int i, float f, float f2) {
        switch (uItem.id) {
            case 2002: presentFragment(new DrawerPreferencesActivity()); break;
            case ID_GENERAL:
                presentFragment(new GeneralPreferencesActivity());
                break;
            case ID_APPEARANCE:
                presentFragment(new AppearancePreferencesActivity());
                break;
            case ID_CHATS:
                presentFragment(new ChatsPreferencesActivity());
                break;
            case ID_CAMERA:
                presentFragment(new CameraPreferencesActivity());
                break;
            case ID_PRIVACY:
                presentFragment(new PrivacyPreferencesActivity());
                break;
            case ID_AYUMOMENTS:
                presentFragment(new AyuMomentsPreferencesActivity());
                break;
            case ID_PLUGINS:
                presentFragment(new PluginsActivity());
                break;
            case ID_NIMARKO_MEDIA:
                presentFragment(new NimarkoMediaPreferencesActivity());
                break;
            case ID_BANNERS:
                presentFragment(new BannerPreferencesActivity());
                break;
            case ID_WSBYPASS:
                presentFragment(new WsBypassPreferencesActivity());
                break;
            case ID_PILLSTACK:
                presentFragment(new app.nimarkogram.messenger.infocards.preferences.InfoCardsPreferencesActivity());
                break;
            case ID_TEXTANIM:
                presentFragment(new NimarkoTextAnimPreferencesActivity());
                break;
            case ID_UPDATES:
                app.nimarkogram.messenger.updater.LinkiUpdateSheet.show(this);
                break;
            case ID_DEBUG:
                presentFragment(new DebugPreferencesActivity());
                break;
            case ID_EXPERIMENTAL:
                presentFragment(new ExperimentalPreferencesActivity());
                break;
            case ID_RESTART:
                AppRestartHelper.triggerRebirth(getParentActivity() != null ? getParentActivity() : getContext());
                break;
            case ID_SOURCE_CODE:
                org.telegram.messenger.browser.Browser.openUrl(
                        getParentActivity() != null ? getParentActivity() : getContext(),
                        SOURCE_REPOSITORY_URL);
                break;
            case ID_CHERRYGRAM_FORK:
                org.telegram.messenger.browser.Browser.openUrl(getParentActivity(),
                        "https://github.com/arslan4k1390/Cherrygram");
                break;
            case ID_EASTER:
                handleEasterEggClick();
                break;
            default:
                break;
        }
    }

    private void handleEasterEggClick() {
        long now = System.currentTimeMillis();
        if (now - easterEggLastClickTime > 1500) {
            easterEggClicks = 0;
        }
        easterEggLastClickTime = now;
        easterEggClicks++;
        if (easterEggClicks >= 10) {
            easterEggClicks = 0;
            showEasterEggPhoto();
        }
    }

    private void showEasterEggPhoto() {
        android.content.Context context = getParentActivity() != null ? getParentActivity() : getContext();
        if (context == null) return;
        android.widget.ImageView imageView = new android.widget.ImageView(context);
        android.graphics.drawable.Drawable d = context.getResources().getDrawable(R.drawable.easter_egg);
        imageView.setImageDrawable(d);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        org.telegram.ui.ActionBar.BottomSheet bottomSheet = new org.telegram.ui.ActionBar.BottomSheet(context, false);
        bottomSheet.setCustomView(imageView);
        bottomSheet.setBackgroundColor(0xff000000);
        bottomSheet.show();
    }
}
