package app.nimarkogram.messenger.features;

import java.io.File;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import app.exteraless.speech.VoskTranscriber;
import com.radolyn.ayugram.messages.AyuSpyController;

public final class MessageTools {
    public static void showTimes(BaseFragment fragment, MessageObject message) {
        Utilities.globalQueue.postRunnable(() -> {
            int read = AyuSpyController.getReadDate(message);
            int played = AyuSpyController.getContentsReadDate(message);
            StringBuilder text = new StringBuilder();
            if (read > 0) text.append(LocaleController.formatString(R.string.LX_Read, AyuSpyController.formatPlayedDate(read)));
            if (played > 0) { if (text.length() > 0) text.append('\n'); text.append(LocaleController.formatString(R.string.LX_Played, AyuSpyController.formatPlayedDate(played))); }
            AndroidUtilities.runOnUIThread(() -> {
                if (fragment.getParentActivity() == null) return;
                fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity())
                        .setTitle(LocaleController.getString(R.string.LX_LocalTimes))
                        .setMessage(text.length() == 0 ? LocaleController.getString(R.string.LX_NoTimes) : text.toString())
                        .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
            });
        });
    }
    public static void transcribe(BaseFragment fragment, MessageObject message) {
        if (fragment.getParentActivity() == null) return;
        if (!VoskTranscriber.isReady()) {
            fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity()).setMessage(LocaleController.getString(R.string.LX_NoModel))
                    .setPositiveButton(LocaleController.getString(R.string.Settings), (dialog, which) -> fragment.presentFragment(new app.exteraless.speech.VoskSettingsActivity()))
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
            return;
        }
        File file = message.messageOwner.attachPath == null ? null : new File(message.messageOwner.attachPath);
        if (file == null || !file.isFile()) file = FileLoader.getInstance(message.currentAccount).getPathToMessage(message.messageOwner);
        if (file == null || !file.isFile()) {
            fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity()).setMessage(LocaleController.getString(R.string.LX_RecognitionError))
                    .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
            return;
        }
        AlertDialog progress = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setMessage(LocaleController.getString(R.string.LX_Transcribing));
        fragment.showDialog(progress);
        VoskTranscriber.transcribe(file.getAbsolutePath(), (text, error) -> AndroidUtilities.runOnUIThread(() -> {
            progress.dismiss();
            if (fragment.getParentActivity() == null) return;
            boolean ok = error == null && text != null && !text.trim().isEmpty();
            AlertDialog.Builder result = new AlertDialog.Builder(fragment.getParentActivity())
                    .setTitle(LocaleController.getString(R.string.LX_Offline))
                    .setMessage(ok ? text : LocaleController.getString(R.string.LX_RecognitionError))
                    .setPositiveButton(LocaleController.getString(R.string.OK), null);
            if (ok) result.setNeutralButton(LocaleController.getString(R.string.Copy), (dialog, which) -> AndroidUtilities.addToClipboard(text));
            fragment.showDialog(result.create());
        }));
    }
    private MessageTools() { }
}
