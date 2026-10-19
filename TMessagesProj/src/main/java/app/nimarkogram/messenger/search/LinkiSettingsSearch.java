package app.nimarkogram.messenger.search;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.ProfileActivity.SearchAdapter.SearchResult;

/** Static labels only: indexing never instantiates settings screens or reads their values. */
public final class LinkiSettingsSearch {
    private LinkiSettingsSearch() {}
    private static Resources englishResources;
    private static String englishLabel(int resource) {
        if (englishResources == null) {
            Configuration config = new Configuration(ApplicationLoader.applicationContext.getResources().getConfiguration());
            config.setLocale(Locale.ENGLISH);
            englishResources = ApplicationLoader.applicationContext.createConfigurationContext(config).getResources();
        }
        return englishResources.getString(resource);
    }
    public interface Factory { BaseFragment create(int account); }
    public interface Target { void setSearchTarget(int titleResource); }
    private static class Result extends SearchResult {
        final String keywords;
        Result(int id, String label, String section, String english, Runnable open) {
            super(id, label, null, LocaleController.getString(R.string.NimarkoGramSettings), section,
                    R.drawable.msg_settings_solar, open);
            if (section.equals(LocaleController.getString(R.string.NimarkoGramSettings))) path = new String[]{section};
            keywords = label + " " + section + " " + LocaleController.getString(R.string.NimarkoGramSettings)
                    + " LinkiGram Linki Линкиграм Линки " + english;
        }
    }
    public static SearchResult[] append(SearchResult[] stock, BaseFragment host) {
        ArrayList<SearchResult> all = new ArrayList<>(Arrays.asList(stock));
        LinkiSettingsCatalog.add(all, host);
        return all.toArray(new SearchResult[0]);
    }
    static void add(ArrayList<SearchResult> all, BaseFragment host, String key, int title, int section, Factory factory) {
        addRow(all, host, key, title, section, factory, null);
    }
    static void addRow(ArrayList<SearchResult> all, BaseFragment host, String key, int title, int section, Factory factory, String row) {
        String label = LocaleController.getString(title);
        String sectionTitle = LocaleController.getString(section);
        int id = 0x40000000 | (key.hashCode() & 0x3fffffff);
        Result result = new Result(id, label, sectionTitle, englishLabel(title) + " " + englishLabel(section), () -> {
            BaseFragment target = factory.create(host.getCurrentAccount());
            target.setCurrentAccount(host.getCurrentAccount());
            if (title != section && target instanceof Target) ((Target) target).setSearchTarget(title);
            host.presentFragment(target);
        });
        result.rowName = row;
        all.add(result);
    }
    public static boolean isLinkiResult(SearchResult result) { return result instanceof Result; }
    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
    }
    private static boolean word(String text, String token) { return (" " + text).contains(" " + token); }
    public static CharSequence match(SearchResult item, String query, int color) {
        Result result = (Result) item;
        String indexed = normalize(result.keywords);
        String translit = normalize(LocaleController.getInstance().getTranslitString(indexed));
        String[] tokens = normalize(query).split("\\s+");
        SpannableStringBuilder display = new SpannableStringBuilder(result.searchTitle);
        String title = " " + normalize(result.searchTitle);
        boolean any = false;
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            any = true;
            String latinToken = normalize(LocaleController.getInstance().getTranslitString(token));
            if (!word(indexed, token) && !word(translit, latinToken)) return null;
            int start = title.indexOf(" " + token);
            if (start >= 0 && start + token.length() <= display.length())
                display.setSpan(new ForegroundColorSpan(color), start, start + token.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return any ? display : null;
    }
    public static void focus(BaseFragment fragment, UniversalRecyclerView list, int title) {
        if (list == null || list.adapter == null || title == 0) return;
        String label = LocaleController.getString(title);
        list.post(() -> {
            if (fragment.isFinished || list.getParent() == null) return;
            int found = -1;
            for (int i = 0; i < list.adapter.getItemCount(); i++) {
                UItem item = list.adapter.getItem(i);
                if (item != null && TextUtils.equals(item.text, label)) { found = i; break; }
            }
            if (found >= 0) {
                final int position = found;
                list.layoutManager.scrollToPositionWithOffset(position, AndroidUtilities.dp(60));
                list.highlightRow(() -> position);
            } else {
                // Conditional settings remain conditional; searching must never enable a feature.
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.info,
                        LocaleController.getString(R.string.LS_Conditional)).show();
            }
        });
    }
}
