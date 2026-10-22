package app.nimarkogram.messenger.preferences;

import android.view.View;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import app.nimarkogram.messenger.media.OnceMediaArchive;

public final class OnceMediaArchiveActivity extends BasePreferencesActivity {
    private final ArrayList<File> files = new ArrayList<>();
    private File viewing;
    private long listedUid;
    private boolean opening;

    @Override public String getTitle() { return LocaleController.getString(R.string.LinkiOnceArchive); }

    @Override public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        files.clear();
        listedUid = UserConfig.getInstance(currentAccount).getClientUserId();
        File[] found = OnceMediaArchive.directory(currentAccount).listFiles(file -> file.isFile()
                && (file.getName().endsWith(".jpg") || file.getName().endsWith(".mp4")
                || file.getName().endsWith(".ogg") || file.getName().endsWith(".gif")));
        if (found != null) {
            Arrays.sort(found, Comparator.comparingLong(File::lastModified).reversed());
            files.addAll(Arrays.asList(found));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.LinkiOnceArchiveInfo)));
        if (files.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.LinkiOnceArchiveEmpty)));
        } else {
            for (int i = 0; i < files.size(); i++) {
                File file = files.get(i);
                items.add(UItem.asButton(i + 1, R.drawable.msg_saved, label(file),
                        LocaleController.formatDateTime(file.lastModified() / 1000, true)));
            }
            UItem clear = UItem.asButton(-1, R.drawable.msg_delete, LocaleController.getString(R.string.LinkiOnceArchiveClear));
            clear.red = true; items.add(clear);
        }
    }

    @Override public void onClick(UItem item, View view, int position, float x, float y) {
        if (getParentActivity() == null || listedUid != UserConfig.getInstance(currentAccount).getClientUserId()) return;
        final long owner = listedUid;
        if (item.id == -1) {
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getTitle())
                    .setMessage(LocaleController.getString(R.string.LinkiOnceArchiveClearConfirm))
                    .setPositiveButton(LocaleController.getString(R.string.Delete), (d, which) -> {
                        if (UserConfig.getInstance(currentAccount).getClientUserId() == owner) {
                            OnceMediaArchive.clear(currentAccount, this::reload);
                        }
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
        } else if (item.id > 0 && item.id <= files.size()) {
            File file = files.get(item.id - 1);
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(label(file))
                    .setItems(new CharSequence[]{LocaleController.getString(R.string.Open), LocaleController.getString(R.string.Delete)},
                            (dialog, which) -> {
                                if (UserConfig.getInstance(currentAccount).getClientUserId() != owner) return;
                                if (which == 0) open(file, owner);
                                else Utilities.globalQueue.postRunnable(() -> {
                                    OnceMediaArchive.forget(currentAccount, file);
                                    file.delete(); AndroidUtilities.runOnUIThread(this::reload);
                                });
                            }).create());
        }
    }

    private String label(File file) {
        String name = file.getName();
        String kind = LocaleController.getString(name.endsWith(".ogg") ? R.string.LinkiOnceAudio
                : name.endsWith(".mp4") ? R.string.LinkiOnceVideo : R.string.LinkiOncePhoto);
        try {
            long peer = Long.parseLong(name.substring(0, name.indexOf('_')));
            if (peer > 0) {
                org.telegram.tgnet.TLRPC.User user = getMessagesController().getUser(peer);
                if (user != null) return kind + " · " + org.telegram.messenger.UserObject.getUserName(user);
            } else {
                org.telegram.tgnet.TLRPC.Chat chat = getMessagesController().getChat(-peer);
                if (chat != null) return kind + " · " + chat.title;
            }
        } catch (Exception ignored) { }
        return kind;
    }

    private void open(File source, long owner) {
        if (opening) return;
        opening = true;
        Utilities.globalQueue.postRunnable(() -> {
            File temp = null;
            try {
                File folder = new File(ApplicationLoader.getFilesDirFixed(), "temp");
                if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("Temporary directory unavailable");
                String ext = source.getName().substring(source.getName().lastIndexOf('.'));
                temp = File.createTempFile("once_view_", ext, folder);
                if (!AndroidUtilities.copyFile(source, temp)) throw new java.io.IOException("Copy failed");
                File ready = temp;
                AndroidUtilities.runOnUIThread(() -> {
                    opening = false;
                    if (isFinished || getParentActivity() == null
                            || UserConfig.getInstance(currentAccount).getClientUserId() != owner) { ready.delete(); return; }
                    if (viewing != null) viewing.delete();
                    viewing = ready;
                    String mime = ext.equals(".ogg") ? "audio/ogg" : ext.equals(".mp4") ? "video/mp4"
                            : ext.equals(".gif") ? "image/gif" : "image/jpeg";
                    AndroidUtilities.openForView(ready, source.getName(), mime, getParentActivity(), getResourceProvider(), false);
                });
            } catch (Exception e) {
                if (temp != null) temp.delete(); FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    opening = false;
                    if (!isFinished) BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                            LocaleController.getString(R.string.LinkiOnceArchiveOpenError)).show();
                });
            }
        });
    }

    private void reload() { if (!isFinished && listView != null && listView.adapter != null) listView.adapter.update(true); }

    @Override public void onResume() {
        super.onResume();
        if (viewing != null) { viewing.delete(); viewing = null; }
        reload();
    }

    @Override public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (viewing != null) { viewing.delete(); viewing = null; }
    }
}
