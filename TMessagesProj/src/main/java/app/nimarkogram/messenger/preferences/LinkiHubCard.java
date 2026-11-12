/**
 * This file is part of LinkiGram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * LinkiGram modifications:
 * Copyright Ettacent, 2026.
 */

package app.nimarkogram.messenger.preferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;

import app.nimarkogram.messenger.appearance.ClientTheme;
import app.nimarkogram.messenger.appearance.SettingsIconColors;

/**
 * LinkiGram settings hub card, styled after the exDesktop hub: a rounded tile
 * with a colored icon badge, a title and a short two-line description.
 *
 * <p>The card keeps the same icon palette contract as {@code SettingsActivity.SettingCell},
 * so the "settings icon colour" preference applies here as well.
 */
public class LinkiHubCard extends LinearLayout {

    public interface OnCardClickListener {
        void onCardClick(int id);
    }

    private final FrameLayout iconLayout;
    private final ImageView iconView;
    private final TextView titleView;
    private final TextView subtitleView;
    private final int iconTop;
    private final int iconBottom;

    public static LinkiHubCard create(Context context, int id, IconBackgroundColors colors, int icon,
                                      CharSequence title, CharSequence subtitle,
                                      OnCardClickListener listener) {
        LinkiHubCard card = new LinkiHubCard(context, colors);
        card.setContent(icon, title, subtitle);
        card.setOnClickListener(v -> listener.onCardClick(id));
        return card;
    }

    public LinkiHubCard(Context context, IconBackgroundColors colors) {
        super(context);
        this.iconTop = colors.top;
        this.iconBottom = colors.bottom;

        setOrientation(VERTICAL);
        setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(13), AndroidUtilities.dp(14), AndroidUtilities.dp(13));
        setBackground(cardBackground());

        iconLayout = new FrameLayout(context);
        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconLayout.addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        addView(iconLayout, LayoutHelper.createLinear(40, 40));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setMaxLines(2);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        // Keep every card on the grid the same height, like the exDesktop hub.
        subtitleView.setMinLines(2);
        addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
    }

    public void setContent(int icon, CharSequence title, CharSequence subtitle) {
        applyIconStyle(icon);
        titleView.setText(title);
        subtitleView.setText(subtitle);
    }

    private void applyIconStyle(int icon) {
        final boolean dark = Theme.isCurrentThemeDark();
        final int mode = SettingsIconColors.mode();
        int plateTop = iconTop;
        int plateBottom = iconBottom;
        int glyph = Color.WHITE;
        if (mode == SettingsIconColors.NEUTRAL
                || mode == SettingsIconColors.ACCENT
                || mode == SettingsIconColors.CUSTOM) {
            plateTop = plateBottom = dark ? 0xFF2E2E33 : 0xFFE6E6EB;
            glyph = dark ? 0xFFF2F2F7 : 0xFF17171A;
            if (mode == SettingsIconColors.ACCENT || mode == SettingsIconColors.CUSTOM) {
                glyph = mode == SettingsIconColors.CUSTOM
                        ? SettingsIconColors.customColor()
                        : Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
                plateTop = plateBottom = ColorUtils.blendARGB(
                        Theme.getColor(Theme.key_windowBackgroundWhite), glyph, dark ? 0.22f : 0.12f);
            }
        }
        GradientDrawable plate = new GradientDrawable();
        plate.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        plate.setColors(new int[]{plateTop, plateBottom});
        plate.setCornerRadius(AndroidUtilities.dp(13));
        iconLayout.setBackground(plate);
        iconView.setImageResource(icon);
        iconView.setColorFilter(new PorterDuffColorFilter(glyph, PorterDuff.Mode.SRC_IN));
    }

    @SuppressLint("NewApi")
    private RippleDrawable cardBackground() {
        final float radius = AndroidUtilities.dp(18);
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(radius);
        fill.setColor(cardColor());
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(radius);
        mask.setColor(Color.WHITE);
        final int ripple = Theme.isCurrentThemeDark() ? 0x22FFFFFF : 0x18000000;
        return new RippleDrawable(ColorStateList.valueOf(ripple), fill, mask);
    }

    private static int cardColor() {
        if (ClientTheme.glassEnabled()) {
            return ClientTheme.glassColor(ClientTheme.SURFACE);
        }
        return Theme.getColor(Theme.key_windowBackgroundWhite);
    }
}
