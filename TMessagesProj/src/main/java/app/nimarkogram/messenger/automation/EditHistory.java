package app.nimarkogram.messenger.automation;

import android.text.TextUtils;
import com.radolyn.ayugram.messages.AyuMessagesController;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

public final class EditHistory {
    private EditHistory() {}
    public static void show(BaseFragment fragment, int account, MessageObject message) {
        final long user = UserConfig.getInstance(account).getClientUserId();
        final long dialog = message.getDialogId();
        final int id = message.getId();
        final String current = message.messageOwner.message;
        Utilities.globalQueue.postRunnable(() -> {
            StringBuilder text = new StringBuilder();
            try {
                for (var revision : AyuMessagesController.getInstance().getRevisions(user, dialog, id)) {
                    if (text.length() > 50000) break;
                    long date = revision.editDate != 0 ? revision.editDate : revision.date;
                    text.append(LocaleController.formatDateTime(date, true)).append('\n');
                    text.append(TextUtils.isEmpty(revision.text)
                            ? LocaleController.getString(R.string.LinkiHistoryMedia) : revision.text).append("\n\n");
                }
            } catch (Exception e) { FileLog.e(e); }
            if (text.length() == 0) text.append(LocaleController.getString(R.string.LinkiHistoryEmpty)).append("\n\n");
            text.append(LocaleController.getString(R.string.LinkiHistoryCurrent)).append("\n").append(current == null ? "" : current);
            AndroidUtilities.runOnUIThread(() -> {
                if (fragment.getParentActivity() == null || UserConfig.getInstance(account).getClientUserId() != user) return;
                AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
                builder.setTitle(LocaleController.getString(R.string.LinkiEditHistory));
                builder.setMessage(text.toString());
                builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                fragment.showDialog(builder.create());
            });
        });
    }
}
