package app.nimarkogram.messenger.preferences;

import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import app.exteraless.ai.AiController;
import app.nimarkogram.messenger.automation.ChatAssistant;
import app.nimarkogram.messenger.preferences.helpers.SettingsHelper;
import java.util.ArrayList;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import static org.telegram.messenger.LocaleController.getString;

public class ChatAssistantPreferencesActivity extends NimarkoUniversalPreferencesActivity {
    private final long dialog;
    public ChatAssistantPreferencesActivity(int account, long dialog) { this.currentAccount = account; this.dialog = dialog; }
    @Override protected CharSequence getTitle() { return getString(R.string.LinkiChatAssistant); }
    @Override public boolean onLongClick(UItem item, View view, int position, float x, float y) { return false; }
    @Override public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(SettingsHelper.asSwitchCG(1, getString(R.string.LinkiChatAssistantEnabled)).setChecked(ChatAssistant.enabled(currentAccount, dialog)));
        items.add(UItem.asShadow(getString(R.string.LinkiChatAssistantInfo)));
        if (dialog < 0) items.add(SettingsHelper.asSwitchCG(2, getString(R.string.LinkiChatAssistantMentions))
                .setChecked(ChatAssistant.prefs().getBoolean(ChatAssistant.key(currentAccount, dialog) + ":mentions", true)));
        items.add(UItem.asButton(3, getString(R.string.LinkiChatAssistantPrompt), ChatAssistant.prompt(currentAccount, dialog)));
        items.add(UItem.asButton(4, getString(R.string.OEAiTitle)));
        items.add(UItem.asButton(5, getString(R.string.OEAiClearHistory)));
        String error = ChatAssistant.prefs().getString(ChatAssistant.key(currentAccount, dialog) + ":error", "");
        if (!error.isEmpty()) items.add(UItem.asShadow(getString(R.string.OEAiFailed) + " · " + error));
    }
    @Override public void onClick(UItem item, View view, int position, float x, float y) {
        String key = ChatAssistant.key(currentAccount, dialog);
        if (item.id == 1) {
            if (!ChatAssistant.enabled(currentAccount, dialog) && (!AiController.canUseAI() || DialogObject.isEncryptedDialog(dialog))) {
                new AlertDialog.Builder(getParentActivity()).setMessage(getString(R.string.OEAiNotConfigured))
                        .setPositiveButton(getString(R.string.OK), null).show(); return;
            }
            ChatAssistant.enable(currentAccount, dialog, !ChatAssistant.enabled(currentAccount, dialog));
            listView.adapter.update(true);
        } else if (item.id == 2) {
            ChatAssistant.prefs().edit().putBoolean(key + ":mentions", !ChatAssistant.prefs().getBoolean(key + ":mentions", true)).apply();
            listView.adapter.update(true);
        } else if (item.id == 4) presentFragment(new app.exteraless.ai.ui.AiSettingsActivity());
        else if (item.id == 5) { ChatAssistant.clear(currentAccount, dialog); }
        else if (item.id == 3) {
            EditText input = new EditText(getParentActivity());
            input.setText(ChatAssistant.prompt(currentAccount, dialog));
            input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(8000) });
            FrameLayout container = new FrameLayout(getParentActivity());
            container.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(8), AndroidUtilities.dp(22), AndroidUtilities.dp(8));
            container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(getString(R.string.LinkiChatAssistantPrompt)); builder.setView(container);
            builder.setPositiveButton(getString(R.string.OK), (d, w) -> {
                ChatAssistant.prefs().edit().putString(key + ":prompt", input.getText().toString()).apply();
                ChatAssistant.clear(currentAccount, dialog); listView.adapter.update(true);
            });
            builder.setNegativeButton(getString(R.string.Cancel), null); showDialog(builder.create());
        }
    }
}
