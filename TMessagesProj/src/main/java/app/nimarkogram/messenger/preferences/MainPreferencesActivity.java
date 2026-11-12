package app.nimarkogram.messenger.preferences;

import android.content.Context;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.nimarkogram.messenger.plugins.PluginsController;
import app.nimarkogram.messenger.plugins.ui.PluginsActivity;
import app.nimarkogram.messenger.utils.AppRestartHelper;
import app.nimarkogram.messenger.wsbypass.preferences.WsBypassPreferencesActivity;

import java.util.ArrayList;
import java.util.Locale;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

/**
 * LinkiGram settings hub, laid out after the exDesktop hub: a compact header,
 * a search field and a two-column grid of cards.
 *
 * <p>The whole hub is a single custom list item, so the grid can keep the exact
 * look (per-card rounded backgrounds, equal heights) and its own search field.
 */
public class MainPreferencesActivity extends BasePreferencesActivity {

    public static final int ID_GENERAL      = 1;
    public static final int ID_APPEARANCE   = 2;
    public static final int ID_CHATS        = 3;
    public static final int ID_CAMERA       = 4;
    public static final int ID_PRIVACY      = 6;
    public static final int ID_DIAGNOSTICS  = 33;
    public static final int ID_PRIVACY_MODES = 34;

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
    public static final int ID_LINKI_SHARE    = 35;
    public static final int ID_MESSAGES       = 36;

    private static final int ID_DRAWER_MENU   = 2002;

    private int easterEggClicks = 0;
    private long easterEggLastClickTime = 0;

    private static final String SOURCE_REPOSITORY_URL = "https://github.com/timaa130704/LinkiGram";
    private static final String CHERRYGRAM_URL = "https://github.com/arslan4k1390/Cherrygram";

    private View aboutHeader;
    private EditText searchField;
    private LinearLayout cardsContainer;
    private String searchQuery = "";
    private boolean fillingItems;

    /** One hub entry: a grid card, or a full-width row when {@code subtitle} is 0. */
    private static final class CardDef {
        final int id;
        final IconBackgroundColors colors;
        final int icon;
        final int title;
        final int subtitle;
        final String keywords;

        CardDef(int id, IconBackgroundColors colors, int icon, int title, int subtitle, String keywords) {
            this.id = id;
            this.colors = colors;
            this.icon = icon;
            this.title = title;
            this.subtitle = subtitle;
            this.keywords = keywords;
        }

        boolean matches(String query) {
            if (TextUtils.isEmpty(query)) return true;
            final String needle = normalize(query);
            final String haystack = normalize(LocaleController.getString(title) + " "
                    + (subtitle != 0 ? LocaleController.getString(subtitle) : "") + " " + keywords);
            return haystack.contains(needle);
        }
    }

    @Override
    public void fillItems(ArrayList<UItem> arrayList, UniversalAdapter universalAdapter) {
        fillingItems = true;
        arrayList.add(UItem.asFullyCustom(createHub(getContext())));
        fillingItems = false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
    }

    //region Sections

    private static CardDef card(int id, IconBackgroundColors colors, int icon, int title, int subtitle, String keywords) {
        return new CardDef(id, colors, icon, title, subtitle, keywords);
    }

    private ArrayList<CardDef> mainSection() {
        ArrayList<CardDef> items = new ArrayList<>();
        items.add(card(ID_GENERAL, IconBackgroundColors.BLUE, R.drawable.msg_settings_solar,
                R.string.NM_Cat_General, R.string.NM_HUB_Card_General, "general basic language notifications scroll"));
        items.add(card(ID_APPEARANCE, IconBackgroundColors.PURPLE, R.drawable.msg_theme_solar,
                R.string.NM_Cat_Appearance, R.string.NM_HUB_Card_Appearance, "appearance theme icons chat list folders navigation"));
        items.add(card(ID_CHATS, IconBackgroundColors.BLUE_DEEP, R.drawable.msg_msgbubble3_solar,
                R.string.NM_Cat_Chats, R.string.NM_HUB_Card_Chats, "chats wallpaper night mode animation"));
        items.add(card(ID_MESSAGES, IconBackgroundColors.BLUE, R.drawable.msg_message_solar,
                R.string.NM_Cat_Messages, R.string.NM_HUB_Card_Messages, "messages bubbles menu gestures input media articles"));
        items.add(card(ID_CAMERA, IconBackgroundColors.CYAN, R.drawable.camera_solar,
                R.string.NM_Cat_Camera, R.string.NM_HUB_Card_Camera, "camera video messages quality effects"));
        items.add(card(ID_PRIVACY, IconBackgroundColors.GREEN, R.drawable.msg_secret_solar,
                R.string.NM_Cat_Privacy, R.string.NM_HUB_Card_Privacy, "privacy phone id streamer sessions"));
        items.add(card(ID_PRIVACY_MODES, IconBackgroundColors.GREEN, R.drawable.ghost_solar,
                R.string.NM_QuickPrivacyModes, R.string.NM_HUB_Card_PrivacyModes, "quick privacy stealth modes"));
        items.add(card(ID_AYUMOMENTS, IconBackgroundColors.RED, R.drawable.msg_delete_solar,
                R.string.NM_AyuMoments, R.string.NM_HUB_Card_AyuMoments, "ayu moments ghost deleted"));
        return items;
    }

    private ArrayList<CardDef> miscSection() {
        ArrayList<CardDef> items = new ArrayList<>();
        items.add(card(ID_LINKI_SHARE, IconBackgroundColors.GREEN, R.drawable.msg_secret_solar,
                R.string.NM_LS_Title, R.string.NM_HUB_Card_LinkiShare, "linki share nfc profile exchange"));
        // Full-width action row: no description.
        items.add(card(ID_DRAWER_MENU, IconBackgroundColors.GRAY, R.drawable.msg_customize,
                R.string.LF_Menu, 0, "side menu drawer settings"));
        if (PluginsController.isPluginEngineSupported()) {
            items.add(card(ID_PLUGINS, IconBackgroundColors.PURPLE, R.drawable.msg_plugins,
                    R.string.Plugins, R.string.NM_HUB_Card_Plugins, "plugins engine install check"));
        }
        items.add(card(ID_NIMARKO_MEDIA, IconBackgroundColors.BLUE_DEEP, R.drawable.msg_download_solar,
                R.string.NM_DownloadMedia, R.string.NM_HUB_Card_Media, "media downloader downloads chats channels"));
        items.add(card(ID_BANNERS, IconBackgroundColors.ORANGE, R.drawable.msg_photos_solar,
                R.string.NM_BAN_Title, R.string.NM_HUB_Card_Banners, "banners moderation"));
        items.add(card(ID_WSBYPASS, IconBackgroundColors.GREEN, R.drawable.msg_secret_solar,
                R.string.NM_WSB_Title, R.string.NM_HUB_Card_Bypass, "bypass proxy tunnels ws telegram"));
        items.add(card(ID_TEXTANIM, IconBackgroundColors.PURPLE, R.drawable.msg_edit_solar,
                R.string.NM_TA_Title, R.string.NM_HUB_Card_TextAnim, "text animation effects"));
        items.add(card(ID_PILLSTACK, IconBackgroundColors.CYAN, R.drawable.msg_search_solar,
                R.string.NM_CARDS_Title, R.string.NM_HUB_Card_Cards, "quick info cards chat list"));
        return items;
    }

    private ArrayList<CardDef> advancedSection() {
        ArrayList<CardDef> items = new ArrayList<>();
        items.add(card(ID_DEBUG, IconBackgroundColors.GRAY, R.drawable.msg_info_solar,
                R.string.NM_HUB_Debug, R.string.NM_HUB_Card_Debug, "debug experimental fine tuning"));
        items.add(card(ID_DIAGNOSTICS, IconBackgroundColors.CYAN, R.drawable.msg_info_solar,
                R.string.NM_Diagnostics, R.string.NM_HUB_Card_Diagnostics, "diagnostics logs errors state"));
        items.add(card(ID_SOURCE_CODE, IconBackgroundColors.BLUE_DEEP, R.drawable.msg_link_2_solar,
                R.string.NM_HUB_SourceCode, R.string.NM_HUB_Card_SourceCode, "source code github repository"));
        return items;
    }

    private ArrayList<CardDef> otherSection() {
        ArrayList<CardDef> items = new ArrayList<>();
        items.add(card(ID_RESTART, IconBackgroundColors.ORANGE_DEEP, R.drawable.msg_retry_solar,
                R.string.NM_HUB_Restart, R.string.NM_HUB_Card_Restart, "restart app reboot"));
        items.add(card(ID_UPDATES, IconBackgroundColors.BLUE, R.drawable.msg_info_solar,
                R.string.UP_CheckForUpdates, R.string.NM_HUB_Card_Updates, "updates auto update backup"));
        return items;
    }

    //endregion

    //region Hub view

    private View createHub(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, AndroidUtilities.dp(16));

        if (aboutHeader == null) {
            aboutHeader = createAboutHeader(context);
        }
        // The hub view is rebuilt on every items refresh, so the cached header has
        // to be detached from the previous (now discarded) hub first.
        AndroidUtilities.removeFromParent(aboutHeader);
        root.addView(aboutHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(createSearchBox(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 4, 8, 6));

        cardsContainer = new LinearLayout(context);
        cardsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(cardsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        rebuildCards(context);

        root.addView(createFooter(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 8, 12, 0));
        return root;
    }

    private void rebuildCards(Context context) {
        if (cardsContainer == null) return;
        cardsContainer.removeAllViews();
        boolean any = false;
        any |= addSection(context, R.string.AP_Header_General, mainSection());
        any |= addSection(context, R.string.NM_HUB_Header_Misc, miscSection());
        any |= addSection(context, R.string.NM_HUB_Header_Advanced, advancedSection());
        any |= addSection(context, R.string.NM_HUB_Header_Other, otherSection());
        if (!any) {
            TextView empty = new TextView(context);
            empty.setText(LocaleController.getString(R.string.NoResult));
            empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, AndroidUtilities.dp(28), 0, AndroidUtilities.dp(28));
            cardsContainer.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    private boolean addSection(Context context, int headerRes, ArrayList<CardDef> defs) {
        ArrayList<CardDef> visible = new ArrayList<>();
        for (CardDef def : defs) {
            if (def.matches(searchQuery)) visible.add(def);
        }
        if (visible.isEmpty()) return false;

        HeaderCell header = new HeaderCell(context);
        header.setText(LocaleController.getString(headerRes));
        cardsContainer.addView(header);

        for (int i = 0; i < visible.size(); i++) {
            CardDef first = visible.get(i);
            if (first.subtitle == 0) {
                cardsContainer.addView(createWideRow(context, first));
                continue;
            }
            CardDef second = (i + 1 < visible.size() && visible.get(i + 1).subtitle != 0) ? visible.get(i + 1) : null;
            if (second != null) i++;
            cardsContainer.addView(createCardRow(context, first, second));
        }
        return true;
    }

    private View createCardRow(Context context, CardDef first, CardDef second) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(3), AndroidUtilities.dp(8), AndroidUtilities.dp(3));

        row.addView(createCard(context, first), cardParams(second != null, true));
        if (second != null) {
            row.addView(createCard(context, second), cardParams(false, false));
        } else {
            View spacer = new View(context);
            row.addView(spacer, cardParams(false, false));
        }
        return row;
    }

    private static LinearLayout.LayoutParams cardParams(boolean hasSibling, boolean left) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f);
        if (hasSibling) {
            if (left) {
                params.rightMargin = AndroidUtilities.dp(6);
            } else {
                params.leftMargin = AndroidUtilities.dp(6);
            }
        }
        return params;
    }

    private View createCard(Context context, CardDef def) {
        return LinkiHubCard.create(context, def.id, def.colors, def.icon,
                LocaleController.getString(def.title), LocaleController.getString(def.subtitle),
                this::handleItemClick);
    }

    private View createWideRow(Context context, CardDef def) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        row.setBackground(rounded(context, AndroidUtilities.dp(16), cardColor()));
        row.setOnClickListener(v -> handleItemClick(def.id));

        ImageView icon = new ImageView(context);
        icon.setImageResource(def.icon);
        icon.setColorFilter(new android.graphics.PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), android.graphics.PorterDuff.Mode.SRC_IN));
        row.addView(icon, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL));

        TextView title = new TextView(context);
        title.setText(LocaleController.getString(def.title));
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        row.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(3), AndroidUtilities.dp(8), AndroidUtilities.dp(3));
        wrapper.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 54));
        return wrapper;
    }

    private View createSearchBox(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
        box.setBackground(rounded(context, AndroidUtilities.dp(16), cardColor()));

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.outline_header_search);
        icon.setColorFilter(new android.graphics.PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), android.graphics.PorterDuff.Mode.SRC_IN));
        box.addView(icon, LayoutHelper.createLinear(20, 20, Gravity.CENTER_VERTICAL));

        searchField = new EditText(context);
        searchField.setBackground(null);
        searchField.setSingleLine(true);
        searchField.setHint(LocaleController.getString(R.string.Search));
        searchField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        searchField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        searchField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        searchField.setInputType(InputType.TYPE_CLASS_TEXT);
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchField.setText(searchQuery);
        if (!TextUtils.isEmpty(searchQuery)) {
            searchField.setSelection(searchQuery.length());
        }
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (fillingItems) return;
                searchQuery = s == null ? "" : s.toString();
                rebuildCards(getContext());
            }
        });
        box.addView(searchField, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 10, 0, 0, 0));

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(2), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        wrapper.addView(box, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46));
        return wrapper;
    }

    private View createFooter(Context context) {
        TextView footer = new TextView(context);
        footer.setText(Html.fromHtml(LocaleController.getString(R.string.NM_HUB_ForkedFromCherrygramSub)));
        footer.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        footer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        footer.setGravity(Gravity.CENTER);
        footer.setOnClickListener(v -> handleEasterEggClick());
        return footer;
    }

    private static android.graphics.drawable.GradientDrawable rounded(Context context, float radius, int color) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setCornerRadius(radius);
        drawable.setColor(color);
        return drawable;
    }

    private static int cardColor() {
        if (app.nimarkogram.messenger.appearance.ClientTheme.glassEnabled()) {
            return app.nimarkogram.messenger.appearance.ClientTheme.glassColor(
                    app.nimarkogram.messenger.appearance.ClientTheme.SURFACE);
        }
        return Theme.getColor(Theme.key_windowBackgroundWhite);
    }

    //endregion

    private View createAboutHeader(Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

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
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(58), AndroidUtilities.dp(58));
        iconParams.bottomMargin = AndroidUtilities.dp(8);
        header.addView(icon, iconParams);

        TextView name = new TextView(context);
        name.setText("LinkiGram");
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
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
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams versionParams = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        versionParams.topMargin = AndroidUtilities.dp(2);
        header.addView(version, versionParams);

        return header;
    }

    @Override
    public String getTitle() {
        return LocaleController.getString(R.string.NimarkoGramSettings);
    }

    private void handleItemClick(int id) {
        switch (id) {
            case ID_DRAWER_MENU:
                presentFragment(new DrawerPreferencesActivity());
                break;
            case ID_LINKI_SHARE:
                presentFragment(new app.nimarkogram.messenger.nfc.LinkiShareActivity());
                break;
            case ID_GENERAL:
                presentFragment(new GeneralPreferencesActivity());
                break;
            case ID_APPEARANCE:
                presentFragment(new AppearancePreferencesActivity());
                break;
            case ID_CHATS:
                presentFragment(new ChatsPreferencesActivity());
                break;
            case ID_MESSAGES:
                presentFragment(new MessagesPreferencesActivity());
                break;
            case ID_CAMERA:
                presentFragment(new CameraPreferencesActivity());
                break;
            case ID_PRIVACY:
                presentFragment(new PrivacyPreferencesActivity());
                break;
            case ID_PRIVACY_MODES:
                presentFragment(new QuickPrivacyModesActivity());
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
            case ID_DIAGNOSTICS:
                presentFragment(new LinkiDiagnosticsActivity());
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
                org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), CHERRYGRAM_URL);
                break;
            case ID_EASTER:
                handleEasterEggClick();
                break;
            default:
                break;
        }
    }

    @Override
    public void onClick(UItem uItem, View view, int i, float f, float f2) {
        handleItemClick(uItem == null ? 0 : uItem.id);
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
