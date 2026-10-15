package app.nimarkogram.messenger.xcfg;

/*
 * Кабинет X-CFG: диалог с состоянием подписки и кнопками.
 * Порт _render_cabinet из плагина X-CFG (автор @x_vare, GPL).
 */
import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AlertDialog;

import java.util.Locale;

public final class XcfgCabinet {

    private XcfgCabinet() {
    }

    private static String fmt(Object v) {
        try {
            String s = String.format(Locale.US, "%.1f", Double.parseDouble(String.valueOf(v)));
            if (s.contains(".")) {
                s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
            }
            return s;
        } catch (Exception e) {
            return "—";
        }
    }

    private interface Action {
        void run();
    }

    static void render(final XcfgController ctl, JSONObject me) {
        Activity activity = ctl.activity();
        if (activity == null) {
            return;
        }

        final int text = ctl.themeColor("text");
        final int hint = ctl.themeColor("hint");
        final int chip = ctl.themeColor("chip");

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(4), AndroidUtilities.dp(22), AndroidUtilities.dp(8));

        class Ui {
            TextView text(String value, float size, int color, boolean bold, int topDp) {
                TextView t = new TextView(activity);
                t.setText(value);
                t.setTextSize(size);
                t.setTextColor(color);
                if (bold) {
                    t.setTypeface(Typeface.DEFAULT_BOLD);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, AndroidUtilities.dp(topDp), 0, 0);
                t.setLayoutParams(lp);
                root.addView(t);
                return t;
            }

            void button(String label, boolean primary, Action onClick, int topDp, final boolean keep) {
                TextView t = new TextView(activity);
                t.setText(label);
                t.setTextSize(15);
                t.setGravity(Gravity.CENTER);
                t.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(13), AndroidUtilities.dp(12), AndroidUtilities.dp(13));
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(AndroidUtilities.dp(14));
                if (primary) {
                    bg.setColor(XcfgPrefs.EMERALD);
                    t.setTextColor(XcfgPrefs.WHITE);
                } else {
                    bg.setColor(chip);
                    t.setTextColor(text);
                }
                t.setBackground(bg);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, AndroidUtilities.dp(topDp), 0, 0);
                t.setLayoutParams(lp);
                t.setOnClickListener(v -> {
                    if (!keep) {
                        ctl.closeCabinetPublic();
                    }
                    onClick.run();
                });
                root.addView(t);
            }
        }
        Ui ui = new Ui();

        String name = me.optString("username", "");
        boolean active = me.optBoolean("active");
        ui.text(name.isEmpty() ? "Ваш аккаунт X-VPN" : "Здравствуйте, " + name, 17, text, true, 4);

        boolean hasSub = me.optBoolean("has_sub");
        if (hasSub) {
            String tariff = me.optString("tariff", "—");
            if (tariff.isEmpty()) {
                tariff = "—";
            }
            ui.text("Тариф " + tariff + " · " + (active ? "активна" : "не активна"), 14, active ? XcfgPrefs.EMERALD : hint, false, 8);
            String expires = me.optString("expires", "");
            if (!expires.isEmpty() && !"null".equals(expires)) {
                ui.text("Действует до " + expires, 13, hint, false, 2);
            }
            String limit = me.optString("limit", "");
            ui.text("Трафик: " + fmt(me.opt("used_gb")) + " ГБ из " + (limit.isEmpty() || "null".equals(limit) ? "—" : limit), 14, text, false, 12);
            trafficBar(root, activity, me, chip);
        } else {
            ui.text("Подписки пока нет.", 14, hint, false, 8);
        }

        if (hasSub && active) {
            if (ctl.vpnRunning()) {
                ui.text("VPN для Telegram включён", 14, XcfgPrefs.EMERALD, true, 14);
                ui.button("Выключить VPN в Telegram", false, () -> ctl.vpnDisable(true), 8, false);
            } else {
                String subUrl = me.optString("sub_url", "");
                ui.button("Включить VPN в Telegram", true, () -> ctl.vpnEnable(subUrl), 16, false);
            }
            ui.button("VPN на весь телефон (приложение X-CFG)", false, ctl::launchXcfg, 10, false);
        } else if (me.optBoolean("trial_available")) {
            ui.button("Получить бесплатный доступ (Free)", true, ctl::trial, 16, false);
        }

        String subUrl = me.optString("sub_url", "");
        if (!subUrl.isEmpty() && !"null".equals(subUrl)) {
            ui.button("Скопировать ссылку подписки", false, () -> {
                AndroidUtilities.addToClipboard(subUrl);
            }, 10, true);
        }
        String bot = me.optString("bot", "");
        if (bot.isEmpty() || "null".equals(bot)) {
            bot = "https://t.me/" + XcfgPrefs.BOT;
        }
        final String botUrl = bot;
        ui.button("Тарифы и продление", false, () -> ctl.openUrlExternal(botUrl), 10, false);
        String support = me.optString("support", "");
        if (support.isEmpty() || "null".equals(support)) {
            support = "https://t.me/x_vare_support";
        }
        final String supportUrl = support;
        ui.button("Поддержка", false, () -> ctl.openUrlExternal(supportUrl), 10, false);
        ui.button("Выйти из X-CFG", false, ctl::logout, 10, false);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(root);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("X-CFG");
        builder.setView(scroll);
        builder.setPositiveButton("Закрыть", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
        ctl.setCabinet(dialog);
    }

    private static void trafficBar(LinearLayout root, Activity activity, JSONObject me, int chip) {
        try {
            double pct = me.optDouble("pct", 0);
            String limit = me.optString("limit", "");
            boolean unlimited = "∞".equals(limit) || "Безлимит".equals(limit) || "безлимит".equals(limit);
            pct = unlimited ? 100.0 : Math.max(0.0, Math.min(100.0, pct));
            LinearLayout bar = new LinearLayout(activity);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            GradientDrawable track = new GradientDrawable();
            track.setCornerRadius(AndroidUtilities.dp(4));
            track.setColor(chip);
            bar.setBackground(track);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(8));
            lp.setMargins(0, AndroidUtilities.dp(6), 0, 0);
            bar.setLayoutParams(lp);
            View fill = new View(activity);
            GradientDrawable fillBg = new GradientDrawable();
            fillBg.setCornerRadius(AndroidUtilities.dp(4));
            fillBg.setColor(pct < 90 ? XcfgPrefs.EMERALD : 0xFFDC5A46);
            fill.setBackground(fillBg);
            fill.setLayoutParams(new LinearLayout.LayoutParams(0, -1, (float) pct));
            View rest = new View(activity);
            rest.setLayoutParams(new LinearLayout.LayoutParams(0, -1, (float) (100.0 - pct)));
            bar.addView(fill);
            bar.addView(rest);
            root.addView(bar);
        } catch (Exception ignore) {
        }
    }
}
