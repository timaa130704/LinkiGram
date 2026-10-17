package app.nimarkogram.messenger.preferences;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadioButton;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

import app.nimarkogram.messenger.NimarkoConfig;
import app.nimarkogram.messenger.icons.icon_replaces.BaseIconReplace;
import app.nimarkogram.messenger.icons.icon_replaces.SolarIconReplace;
import app.nimarkogram.messenger.icons.icon_replaces.LiquidGlassFullReplace;
import app.nimarkogram.messenger.icons.icon_replaces.PlumpyFullReplace;

public class IconPackSelectorActivity extends BaseFragment {

    private final ArrayList<PreviewCell> cells = new ArrayList<>();

    private static final int[] VALUES = {
            NimarkoConfig.ICON_REPLACE_NONE,
            NimarkoConfig.ICON_REPLACE_SOLAR,
            NimarkoConfig.ICON_REPLACE_LIQUID_GLASS,
            NimarkoConfig.ICON_REPLACE_PLUMPY,
            NimarkoConfig.ICON_REPLACE_CUSTOM_SVG,
    };
    private static final int[] TITLES = {
            R.string.NM_IconPack_DefaultTitle, R.string.NM_IconPack_SolarTitle,
            R.string.NM_IconPack_LiquidTitle, R.string.NM_IconPack_PlumpyTitle,
            R.string.NM_IconPack_CustomTitle
    };
    private static final int[] SUBTITLES = {
            R.string.NM_IconPack_DefaultSubtitle, R.string.NM_IconPack_SolarSubtitle,
            R.string.NM_IconPack_LiquidSubtitle, R.string.NM_IconPack_PlumpySubtitle,
            R.string.NM_IconPack_CustomSubtitle
    };

    private static final int[] SAMPLE_RES = {
            R.drawable.msg_settings, R.drawable.msg_calls, R.drawable.msg_folders,
            R.drawable.msg_camera, R.drawable.msg_contacts, R.drawable.msg_archive,
    };
    private static final String[] SAMPLE_NAMES = {
            "msg_settings", "msg_calls", "msg_folders", "msg_camera", "msg_contacts", "msg_archive",
    };
    private static final int REQUEST_IMPORT_SVG_PACK = 45123;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.AP_IconReplacements));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(12));

        for (int i = 0; i < VALUES.length; i++) {
            final int value = VALUES[i];
            PreviewCell cell = new PreviewCell(context, value, getString(TITLES[i]), getString(SUBTITLES[i]));
            cell.setOnClickListener(v -> select(value));
            cells.add(cell);
            list.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    16, i == 0 ? 8 : 6, 16, 0));
        }

        TextView importPack = new TextView(context);
        importPack.setText(getString(R.string.NM_IconPack_ImportSvg));
        importPack.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        importPack.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        importPack.setGravity(Gravity.CENTER);
        importPack.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        importPack.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), Theme.getColor(Theme.key_windowBackgroundWhite)));
        importPack.setOnClickListener(v -> openSvgPackPicker());
        list.addView(importPack, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 12, 16, 4));
        TextView importHint = new TextView(context);
        importHint.setText(getString(R.string.NM_IconPack_ImportSvgHint));
        importHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        importHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        importHint.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(4), AndroidUtilities.dp(16), 0);
        list.addView(importHint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 0, 16, 4));

        ScrollView scroll = new ScrollView(context);
        scroll.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = root;
        return fragmentView;
    }

    private boolean nmApplyPending;
    private int nmApplyGeneration;
    private int nmPendingApplyGeneration;
    private int nmPendingSelection = NimarkoConfig.ICON_REPLACE_NONE;

    private final Runnable nmApplyPackRunnable = () -> {
        final int generation = nmPendingApplyGeneration;
        final int selection = nmPendingSelection;
        nmApplyPending = false;
        if (generation == nmApplyGeneration
                && selection == NimarkoConfig.iconReplacement
                && getParentActivity() instanceof LaunchActivity) {
            ((LaunchActivity) getParentActivity()).reloadResources();   
        }
    };

    private static boolean isKnownValue(int v) {
        for (int known : VALUES) {
            if (known == v) return true;
        }
        return false;
    }

    private static boolean isRowSelected(int rowValue) {
        int stored = NimarkoConfig.iconReplacement;
        if (!isKnownValue(stored)) {
            stored = NimarkoConfig.ICON_REPLACE_NONE;   
        }
        return rowValue == stored;
    }

    private void select(int value) {
        if (value == NimarkoConfig.ICON_REPLACE_CUSTOM_SVG
                && !CustomSvgIconPack.isInstalled(ApplicationLoader.applicationContext)) {
            openSvgPackPicker();
            return;
        }
        if (NimarkoConfig.iconReplacement != value) {
            NimarkoConfig.setIconReplacement(value);
            for (PreviewCell c : cells) c.refreshSelected();   
            
            AndroidUtilities.cancelRunOnUIThread(nmApplyPackRunnable);
            nmPendingApplyGeneration = ++nmApplyGeneration;
            nmPendingSelection = value;
            nmApplyPending = true;
            AndroidUtilities.runOnUIThread(nmApplyPackRunnable, 500);
        }
    }

    private void openSvgPackPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, REQUEST_IMPORT_SVG_PACK);
        } catch (Throwable error) {
            Toast.makeText(getContext(), getString(R.string.NM_IconPack_ImportError), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_IMPORT_SVG_PACK) {
            super.onActivityResultFragment(requestCode, resultCode, data);
            return;
        }
        if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
        final Context context = getContext();
        if (context == null) return;
        final android.net.Uri uri = data.getData();
        Utilities.globalQueue.postRunnable(() -> {
            try {
                CustomSvgIconPack.importZip(context, uri);
                AndroidUtilities.runOnUIThread(() -> {
                    select(NimarkoConfig.ICON_REPLACE_CUSTOM_SVG);
                    for (PreviewCell cell : cells) cell.refreshSelected();
                    if (getParentActivity() instanceof LaunchActivity) {
                        ((LaunchActivity) getParentActivity()).reloadResources();
                    }
                    Toast.makeText(context, getString(R.string.NM_IconPack_Imported), Toast.LENGTH_SHORT).show();
                });
            } catch (Throwable error) {
                org.telegram.messenger.FileLog.e(error);
                AndroidUtilities.runOnUIThread(() -> Toast.makeText(context,
                        getString(R.string.NM_IconPack_ImportError), Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    public void onFragmentDestroy() {
        
        if (nmApplyPending) {
            AndroidUtilities.cancelRunOnUIThread(nmApplyPackRunnable);
            nmApplyPackRunnable.run();
        }
        super.onFragmentDestroy();
    }

    private Drawable[] buildIcons(int value) {
        
        Resources base = ApplicationLoader.rawResources();
        if (base == null) base = ApplicationLoader.applicationContext.getResources();
        int n = SAMPLE_RES.length;
        Drawable[] out = new Drawable[n];
        int tint = Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon);
        
        BaseIconReplace replace = replaceFor(value);
        for (int i = 0; i < n; i++) {
            int resId = SAMPLE_RES[i];
            if (value == NimarkoConfig.ICON_REPLACE_CUSTOM_SVG) {
                out[i] = CustomSvgIconPack.load(base, resId);
                if (out[i] == null) {
                    try { out[i] = base.getDrawable(resId); } catch (Throwable ignore) {}
                }
                if (out[i] != null) out[i] = out[i].mutate();
                continue;
            }
            int wrappedId = replace != null ? replace.wrap(resId) : resId;
            Drawable d = null;
            try { d = base.getDrawable(wrappedId); } catch (Throwable ignore) {}
            if (d != null) {
                d = d.mutate();
                
                d.setColorFilter(new PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN));
            }
            out[i] = d;
        }
        return out;
    }

    private static BaseIconReplace replaceFor(int value) {
        switch (value) {
            case NimarkoConfig.ICON_REPLACE_SOLAR:        return new SolarIconReplace();
            case NimarkoConfig.ICON_REPLACE_LIQUID_GLASS: return new LiquidGlassFullReplace();
            case NimarkoConfig.ICON_REPLACE_PLUMPY:       return new PlumpyFullReplace();
            default:                                       return null;   
        }
    }

    private class PreviewCell extends FrameLayout {
        final int value;
        final RadioButton radio;

        PreviewCell(Context context, int value, String title, String subtitle) {
            super(context);
            this.value = value;

            float rad = AndroidUtilities.dp(14);
            GradientDrawable content = new GradientDrawable();
            content.setShape(GradientDrawable.RECTANGLE);
            content.setCornerRadius(rad);
            content.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            GradientDrawable mask = new GradientDrawable();
            mask.setShape(GradientDrawable.RECTANGLE);
            mask.setCornerRadius(rad);
            mask.setColor(0xffffffff);
            setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Theme.getColor(Theme.key_listSelector)), content, mask));
            org.telegram.ui.Components.ScaleStateListAnimator.apply(this, 0.02f, 1.5f);   

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);

            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);

            TextView name = new TextView(context);
            name.setText(title);
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            name.setTypeface(AndroidUtilities.bold());
            texts.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            TextView sub = new TextView(context);
            sub.setText(subtitle);
            sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            texts.addView(sub, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            header.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

            radio = new RadioButton(context);
            radio.setSize(AndroidUtilities.dp(20));
            radio.setColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_radioBackgroundChecked));
            radio.setChecked(isRowSelected(value), false);
            radio.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            header.addView(radio, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL));

            col.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            LinearLayout iconsRow = new LinearLayout(context);
            iconsRow.setOrientation(LinearLayout.HORIZONTAL);
            Drawable[] icons = buildIcons(value);
            for (Drawable d : icons) {
                ImageView iv = new ImageView(context);
                iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                if (d != null) iv.setImageDrawable(d);
                iconsRow.addView(iv, LayoutHelper.createLinear(30, 30, 0, 0, 14, 0));
            }
            col.addView(iconsRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));

            addView(col, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.TOP, 16, 14, 16, 14));
            setClickable(true);
            setFocusable(true);
            setContentDescription(title + ", " + subtitle);
            setSelected(isRowSelected(value));
        }

        void refreshSelected() {
            radio.setChecked(isRowSelected(value), true);
            setSelected(isRowSelected(value));
            sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.RadioButton");
            info.setCheckable(true);
            info.setChecked(isRowSelected(value));
        }
    }
}
