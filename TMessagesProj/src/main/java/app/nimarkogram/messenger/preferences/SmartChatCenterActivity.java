package app.nimarkogram.messenger.preferences;

import android.os.Bundle;
import android.view.View;
import java.util.ArrayList;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

/** Account-scoped entry point using Telegram's standard list cells. */
public class SmartChatCenterActivity extends NimarkoUniversalPreferencesActivity {
    private final ArrayList<Long> dialogs = new ArrayList<>();
    public SmartChatCenterActivity(int account) { currentAccount = account; }
    @Override public String getTitle() { return LocaleController.getString(R.string.LinkiSmartCenter); }
    @Override public boolean onLongClick(UItem item, View view, int position, float x, float y) { return false; }
    @Override public void onResume() {
        super.onResume();
        if (listView != null) listView.adapter.update(true);
    }
    @Override public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        dialogs.clear();
        items.add(UItem.asButton(-1, LocaleController.getString(R.string.LinkiAISettings)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.LinkiSmartCenterInfo)));
        items.add(UItem.asHeader(LocaleController.getString(R.string.LinkiSmartMentions)));
        addDialogs(items, true);
        items.add(UItem.asHeader(LocaleController.getString(R.string.LinkiSmartUnread)));
        addDialogs(items, false);
    }
    private void addDialogs(ArrayList<UItem> items, boolean mentions) {
        int added = 0;
        for (TLRPC.Dialog dialog : getMessagesController().getAllDialogs()) {
            if (DialogObject.isEncryptedDialog(dialog.id) || dialog.id == 0 || dialog instanceof TLRPC.TL_dialogFolder) continue;
            if (mentions ? dialog.unread_mentions_count <= 0 : dialog.unread_count <= 0 || dialog.unread_mentions_count > 0) continue;
            dialogs.add(dialog.id);
            items.add(UItem.asButton(dialogs.size(), DialogObject.getName(currentAccount, dialog.id),
                    String.valueOf(mentions ? dialog.unread_mentions_count : dialog.unread_count)));
            added++;
        }
        if (added == 0) items.add(UItem.asShadow(LocaleController.getString(R.string.LinkiSmartEmpty)));
    }
    @Override public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == -1) { presentFragment(new app.exteraless.ai.ui.AiSettingsActivity()); return; }
        if (item.id <= 0 || item.id > dialogs.size()) return;
        long dialog = dialogs.get(item.id - 1);
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(DialogObject.getName(currentAccount, dialog))
                .setItems(new String[]{LocaleController.getString(R.string.LinkiSmartOpen), LocaleController.getString(R.string.LinkiSmartSummarize)}, (d, which) -> {
                    if (which == 0) {
                        Bundle args = new Bundle();
                        args.putLong(dialog < 0 ? "chat_id" : "user_id", Math.abs(dialog));
                        presentFragment(new ChatActivity(args));
                    } else app.nimarkogram.messenger.automation.ChatSummary.show(this, dialog);
                }).create());
    }
}
