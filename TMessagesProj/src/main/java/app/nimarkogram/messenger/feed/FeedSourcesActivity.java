package app.nimarkogram.messenger.feed;

import android.view.View;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.*;
import app.nimarkogram.messenger.preferences.NimarkoUniversalPreferencesActivity;
import app.nimarkogram.messenger.preferences.helpers.SettingsHelper;

public class FeedSourcesActivity extends NimarkoUniversalPreferencesActivity {
    private final ArrayList<Long> ids = new ArrayList<>();
    @Override protected CharSequence getTitle() { return LocaleController.getString(R.string.LF_Sources); }
    public static String name(int account, long id) {
        MessagesController mc = MessagesController.getInstance(account);
        if (id < 0) { TLRPC.Chat c = mc.getChat(-id); return c == null ? Long.toString(id) : c.title; }
        TLRPC.User u = mc.getUser(id);
        return u == null ? Long.toString(id) : UserObject.getUserName(u);
    }
    @Override public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        ids.clear();
        Set<String> selected = FeedPreferences.sources(currentAccount);
        items.add(UItem.asShadow(LocaleController.getString(R.string.LF_SourceHelp)));
        // Also retain previously selected sources if their dialog is not in the loaded slice.
        Set<Long> seen = new HashSet<>();
        for (TLRPC.Dialog dialog : getMessagesController().getAllDialogs()) {
            long id = dialog.id;
            if (DialogObject.isEncryptedDialog(id) || DialogObject.isFolderDialogId(id) || id == 0) continue;
            if (id < 0) {
                TLRPC.Chat chat = getMessagesController().getChat(-id);
                if (chat == null || ChatObject.isNotInChat(chat)) continue;
            }
            if (seen.add(id)) ids.add(id);
        }
        for (String raw : selected) {
            try { long id = Long.parseLong(raw); if (seen.add(id)) ids.add(id); } catch (NumberFormatException ignored) {}
        }
        for (int i = 0; i < ids.size(); i++) items.add(SettingsHelper.asSwitchCG(i + 1, name(currentAccount, ids.get(i)))
                .setChecked(selected.contains(Long.toString(ids.get(i)))));
    }
    @Override public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id > 0 && item.id <= ids.size()) {
            FeedPreferences.toggle(currentAccount, ids.get(item.id - 1));
            listView.adapter.update(true);
        }
    }
    @Override public boolean onLongClick(UItem item, View view, int position, float x, float y) { return false; }
}
