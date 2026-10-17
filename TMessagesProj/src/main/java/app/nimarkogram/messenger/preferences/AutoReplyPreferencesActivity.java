package app.nimarkogram.messenger.preferences;

import static org.telegram.messenger.LocaleController.getString;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import app.nimarkogram.messenger.preferences.helpers.SettingsHelper;
import app.nimarkogram.messenger.ai.AutoReplyConfig;
import app.nimarkogram.messenger.ai.AutoReplyClient;
import java.util.ArrayList;
import java.util.List;

/** Separate, opt-in account settings: AI Editor credentials are never reused. */
public class AutoReplyPreferencesActivity extends NimarkoUniversalPreferencesActivity {
    private final long dialogId;
    private final AutoReplyConfig config;
    private boolean alive = true, busy;
    private Thread requestThread;
    private String status = "";
    public AutoReplyPreferencesActivity(int account, long dialogId) {
        currentAccount = account;
        this.dialogId = dialogId;
        config = AutoReplyConfig.get(account);
    }
    @Override protected CharSequence getTitle() { return getString(R.string.AR_Title); }
    @Override public void onFragmentDestroy() { alive = false; if (requestThread != null) requestThread.interrupt(); super.onFragmentDestroy(); }
    private void refresh() { if (alive && listView != null) listView.adapter.update(true); }
    @Override public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (dialogId != 0) {
            JSONObject chat = config.chat(dialogId);
            items.add(SettingsHelper.asSwitchCG(10, getString(R.string.AR_Enable)).setChecked(chat.optBoolean("enabled")));
            items.add(UItem.asButton(11, getString(R.string.AR_Prompt), chat.optString("prompt", "")));
            items.add(UItem.asButton(12, getString(R.string.AR_Length), "" + chat.optInt("max_chars", 500)));
            items.add(UItem.asButton(13, getString(R.string.AR_Context), "" + chat.optInt("context", 0)));
            items.add(UItem.asShadow(getString(R.string.AR_Notice)));
            String error = config.getString("status_" + dialogId, "");
            if (!error.isEmpty()) items.add(UItem.asShadow(error));
            items.add(UItem.asButton(14, getString(R.string.AR_Connection), ""));
            return;
        }
        items.add(UItem.asButton(1, getString(R.string.AR_Provider), config.getString("provider", "OpenAI")));
        items.add(UItem.asButton(2, getString(R.string.AR_Base), config.getString("base", "https://api.openai.com/v1")));
        items.add(UItem.asShadow(getString(R.string.AR_BaseNotice)));
        items.add(UItem.asButton(3, getString(R.string.AR_Key), getString(R.string.AR_EditKey)));
        items.add(UItem.asButton(4, getString(R.string.AR_Model), config.getString("model", "")));
        items.add(UItem.asButton(5, getString(R.string.AR_Models), ""));
        items.add(SettingsHelper.asSwitchCG(6, getString(R.string.AR_Auto)).setChecked(config.getBoolean("auto", false)));
        items.add(UItem.asButton(7, getString(R.string.AR_Benchmark), ""));
        items.add(UItem.asButton(8, getString(R.string.AR_Reset), ""));
        items.add(UItem.asShadow(getString(R.string.AR_SpeedNotice)));
        items.add(UItem.asShadow(busy ? getString(R.string.AR_Working) : status));
        items.add(UItem.asShadow(getString(R.string.AR_ChooseChats)));
    }
    private void error() { status = getString(R.string.AR_SaveError); refresh(); message(status); }
    private void message(String text) { if (alive && getParentActivity() != null) showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.AR_Title)).setMessage(text).setPositiveButton(getString(R.string.OK), null).create()); }
    private interface Save { void apply(String value) throws Exception; }
    private void edit(int title, String value, int type, int limit, Save save) {
        if (getParentActivity() == null) return;
        EditTextBoldCursor input = new EditTextBoldCursor(getParentActivity());
        input.setTextSize(18); input.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        input.setSaveEnabled(false); input.setInputType(type); input.setText(value); input.setSelectAllOnFocus(true);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(limit)});
        LinearLayout box = new LinearLayout(getParentActivity());
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 8));
        AlertDialog dialog = new AlertDialog.Builder(getParentActivity()).setTitle(getString(title)).setView(box)
            .setNegativeButton(getString(R.string.Cancel), null).setPositiveButton(getString(R.string.Done), null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try { save.apply(input.getText().toString().trim()); refresh(); dialog.dismiss(); }
                catch (Exception e) { input.setError(getString(R.string.AR_Invalid)); }
            });
            input.requestFocus(); AndroidUtilities.showKeyboard(input);
        });
        showDialog(dialog);
        if (title == R.string.AR_Key && dialog.getWindow() != null) dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
    }
    private void saveChat(String key, Object value) throws Exception {
        JSONObject chat = config.chat(dialogId); chat.put(key, value); config.saveChat(dialogId, chat); refresh();
    }
    @Override public void onClick(UItem item, View view, int position, float x, float y) {
        try {
            switch (item.id) {
            case 1: edit(R.string.AR_Provider, config.getString("provider", "OpenAI"), InputType.TYPE_CLASS_TEXT, 100, v -> config.putString("provider", v)); break;
            case 2: edit(R.string.AR_Base, config.getString("base", "https://api.openai.com/v1"), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, 2048, v -> { config.putString("base", AutoReplyClient.normalizeBase(v)); config.putString("models", "[]"); }); break;
            case 3: edit(R.string.AR_Key, "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, 4096, v -> config.setKey(v)); break;
            case 4: edit(R.string.AR_Model, config.getString("model", ""), InputType.TYPE_CLASS_TEXT, 200, v -> { config.putString("model", v); config.putBoolean("auto", false); }); break;
            case 5: network(false); break;
            case 6:
                if (config.getBoolean("auto", false)) { config.putBoolean("auto", false); refresh(); }
                else if (!busy) showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.AR_Auto)).setMessage(getString(R.string.AR_TestWarning)).setNegativeButton(getString(R.string.Cancel), null).setPositiveButton(getString(R.string.OK), (d,w) -> { try { config.putBoolean("auto", true); refresh(); network(true); } catch (Exception e) { error(); } }).create());
                break;
            case 7: showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.AR_Benchmark)).setMessage(getString(R.string.AR_TestWarning)).setNegativeButton(getString(R.string.Cancel), null).setPositiveButton(getString(R.string.OK), (d,w) -> network(true)).create()); break;
            case 8: if (busy) { message(getString(R.string.AR_Working)); break; } AutoReplyClient.reset(); status = getString(R.string.AR_ResetDone); refresh(); break;
            case 10:
                if (config.chat(dialogId).optBoolean("enabled")) saveChat("enabled", false);
                else showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.AR_Enable)).setMessage(getString(R.string.AR_Notice)).setNegativeButton(getString(R.string.Cancel), null).setPositiveButton(getString(R.string.AR_Enable), (d,w) -> { try { saveChat("enabled", true); } catch (Exception e) { error(); } }).create());
                break;
            case 11: edit(R.string.AR_Prompt, config.chat(dialogId).optString("prompt", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, 4000, v -> saveChat("prompt", v)); break;
            case 12: edit(R.string.AR_Length, "" + config.chat(dialogId).optInt("max_chars", 500), InputType.TYPE_CLASS_NUMBER, 4, v -> { int n = Integer.parseInt(v); if (n < 50 || n > 4000) throw new IllegalArgumentException(); saveChat("max_chars", n); }); break;
            case 13: edit(R.string.AR_Context, "" + config.chat(dialogId).optInt("context", 0), InputType.TYPE_CLASS_NUMBER, 1, v -> { int n = Integer.parseInt(v); if (n < 0 || n > 6) throw new IllegalArgumentException(); saveChat("context", n); }); break;
            case 14: presentFragment(new AutoReplyPreferencesActivity(currentAccount, 0)); break;
            }
        } catch (Exception e) { error(); }
    }
    @Override public boolean onLongClick(UItem item, View view, int position, float x, float y) { return false; }
    private void network(boolean benchmark) {
        if (busy) return;
        final String base = config.getString("base", "https://api.openai.com/v1");
        final long revision = config.connectionRevision();
        final String key;
        try { key = config.key(); } catch (Exception e) { error(); return; }
        busy = true; refresh();
        requestThread = new Thread(() -> {
            try {
                List<String> models;
                String report = "";
                if (benchmark) {
                    models = new ArrayList<>();
                    JSONArray saved = new JSONArray(config.getString("models", "[]"));
                    for (int i = 0; i < saved.length(); i++) models.add(saved.getString(i));
                    String manual = config.getString("model", "");
                    if (!manual.isEmpty() && !models.contains(manual)) models.add(manual);
                    if (models.isEmpty()) models = AutoReplyClient.models(base, key);
                    java.util.Map<String, Long> measured = AutoReplyClient.benchmark(base, key, models);
                    StringBuilder lines = new StringBuilder();
                    for (java.util.Map.Entry<String, Long> entry : measured.entrySet()) lines.append(entry.getKey()).append(": ").append(entry.getValue() > 0 ? entry.getValue() + " ms" : "×").append("\n");
                    report = lines.toString();
                } else models = AutoReplyClient.models(base, key);
                final List<String> result = models;
                final String measurements = report;
                AndroidUtilities.runOnUIThread(() -> {
                    if (!alive) return;
                    busy = false;
                    if (revision != config.connectionRevision()) { refresh(); return; }
                    try { config.putString("models", new JSONArray(result).toString()); } catch (Exception e) { error(); busy = false; refresh(); return; }
                    status = getString(benchmark ? R.string.AR_TestDone : R.string.AR_ModelsDone) + (benchmark ? "\n" + measurements : ""); refresh();
                    if (!benchmark && !result.isEmpty()) showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.AR_Model)).setItems(result.toArray(new CharSequence[0]), (d,w) -> { try { config.putString("model", result.get(w)); config.putBoolean("auto", false); refresh(); } catch (Exception e) { error(); } }).create());
                });
            } catch (Exception e) {
                AndroidUtilities.runOnUIThread(() -> { if (alive) { busy = false; if (revision != config.connectionRevision()) { refresh(); return; } status = e instanceof AutoReplyClient.ClientException ? e.getMessage() : getString(R.string.AR_NetworkError); refresh(); message(status); } });
            }
        }, "ai-autoreply-settings");
        requestThread.start();
    }
}
