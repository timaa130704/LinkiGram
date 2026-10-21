 
package app.nimarkogram.messenger.preferences;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import app.nimarkogram.messenger.banners.NimarkoBannerConfig;
import app.nimarkogram.messenger.banners.NimarkoBannerController;
import app.nimarkogram.messenger.preferences.helpers.SettingsHelper;

public class BannerPreferencesActivity extends BasePreferencesActivity {

    private static final int FILE_PICK_CODE = 9901;
    private static final long MAX_SIZE = 8L << 20;

    private static final int ID_ENABLED       = 100;
    private static final int ID_STATUS        = 101;
    private static final int ID_CHANGE_GLOBAL = 102;
    private static final int ID_SUBMIT        = 103;
    private static final int ID_HIDE_AVATAR   = 104;
    private static final int ID_REFRESH       = 105;
    private static final int ID_PICK_LOCAL    = 106;
    private static final int ID_DELETE_LOCAL  = 107;
    private static final int ID_USE_AVATAR    = 108;
    private static final int ID_LITE          = 109;
    private static final int ID_SOUND_INFO    = 110;

    private final NimarkoBannerController ctrl = NimarkoBannerController.getInstance();
    private boolean pickingGlobal;
    private Runnable settingsReloader;

    @Override
    public String getTitle() {
        return LocaleController.getString(R.string.NM_BAN_Title);
    }

    @Override
    public boolean onFragmentCreate() {
        ctrl.ensureStarted();
        installSettingsReloader();
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        super.onResume();
        installSettingsReloader();
    }

    @Override
    public void onFragmentDestroy() {
        
        super.onFragmentDestroy();
    }

    private void installSettingsReloader() {
        if (settingsReloader == null) {
            WeakReference<BannerPreferencesActivity> owner = new WeakReference<>(this);
            settingsReloader = () -> {
                BannerPreferencesActivity activity = owner.get();
                if (activity != null && !activity.isFinished) {
                    activity.reload();
                }
            };
        }
        ctrl.setSettingsReloader(settingsReloader);
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        
        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_BAN_Title)));
        items.add(SettingsHelper.asSwitchCG(ID_ENABLED,
                LocaleController.getString(R.string.NM_BAN_Enable),
                LocaleController.getString(R.string.NM_BAN_EnableHint))
                .setChecked(NimarkoBannerConfig.enabled));
        items.add(UItem.asShadow(null));
        if (!NimarkoBannerConfig.enabled) {
            return;
        }

        String st = ctrl.statusString();

        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_BAN_GlobalHeader)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.LinkiBannerModerationHint)));
        items.add(UItem.asButton(ID_STATUS, R.drawable.msg_info,
                LocaleController.getString(R.string.NM_BAN_StatusLabel), statusText(st)));
        switch (st) {
            case "approved":
                items.add(UItem.asButton(ID_CHANGE_GLOBAL, R.drawable.msg_edit,
                        LocaleController.getString(R.string.NM_BAN_ChangeGlobal)));
                break;
            case "pending":
                items.add(UItem.asShadow(LocaleController.getString(R.string.NM_BAN_PendingWarning)));
                break;
            case "blocked":
                items.add(UItem.asShadow(LocaleController.getString(R.string.NM_BAN_BlockedWarning)));
                break;
            default: 
                items.add(UItem.asButton(ID_SUBMIT, R.drawable.msg_send,
                        LocaleController.getString(R.string.NM_BAN_SubmitModeration)));
                break;
        }
        items.add(UItem.asShadow(null));

        if ("approved".equals(st)) {
            items.add(UItem.asHeader(LocaleController.getString(R.string.NM_BAN_AvatarSectionHeader)));
            items.add(SettingsHelper.asSwitchCG(ID_HIDE_AVATAR,
                    LocaleController.getString(R.string.NM_BAN_HideAvatar),
                    LocaleController.getString(R.string.NM_BAN_HideAvatarHint))
                    .setChecked(ctrl.hideAvatarFlag()));
            items.add(UItem.asShadow(null));
        }

        items.add(UItem.asButtonWithSubtext(ID_REFRESH, R.drawable.msg_reset,
                LocaleController.getString(R.string.NM_BAN_RefreshStatus),
                LocaleController.getString(R.string.NM_BAN_RefreshHint), 0, 0));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_BAN_QualityHeader)));
        items.add(SettingsHelper.asSwitchCG(ID_LITE,
                LocaleController.getString(R.string.NM_BAN_LiteMode),
                LocaleController.getString(R.string.NM_BAN_LiteModeHint))
                .setChecked(NimarkoBannerConfig.liteMode));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_BAN_LocalHeader)));
        items.add(SettingsHelper.asSwitchCG(ID_USE_AVATAR,
                LocaleController.getString(R.string.NM_BAN_AvatarBanner),
                LocaleController.getString(R.string.NM_BAN_AvatarHint))
                .setChecked(NimarkoBannerConfig.useAvatar));
        if ("approved".equals(st)) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.NM_BAN_LocalDisabledHint)));
        } else {
            String lp = NimarkoBannerConfig.getLocalBannerPath();
            File f = lp == null ? null : new File(lp);
            String info;
            if (lp != null && !lp.isEmpty() && f.exists()) {
                String kind = lp.toLowerCase().endsWith(".mp4")
                        ? LocaleController.getString(R.string.NM_BAN_VideoLabel)
                        : LocaleController.getString(R.string.NM_BAN_PhotoLabel);
                info = kind + " · " + f.getName();
            } else {
                info = LocaleController.getString(R.string.NM_BAN_NotSet);
            }
            items.add(UItem.asButtonWithSubtext(ID_PICK_LOCAL, R.drawable.msg_photos,
                    LocaleController.getString(R.string.NM_BAN_PickLocal), info, 0, 0));
            if (f != null && f.exists()) {
                items.add(UItem.asButton(ID_DELETE_LOCAL, R.drawable.msg_delete,
                        LocaleController.getString(R.string.NM_BAN_DeleteLocal)));
            }
            items.add(UItem.asShadow(LocaleController.getString(R.string.NM_BAN_LocalOnlyHint)));
        }
        items.add(UItem.asShadow(null));
    }

    private static String statusText(String st) {
        switch (st) {
            case "none":     return LocaleController.getString(R.string.NM_BAN_StatusNone);
            case "pending":  return LocaleController.getString(R.string.NM_BAN_StatusPending);
            case "approved": return LocaleController.getString(R.string.NM_BAN_StatusApproved);
            case "rejected": return LocaleController.getString(R.string.NM_BAN_StatusRejected);
            case "blocked":  return LocaleController.getString(R.string.NM_BAN_StatusBlocked);
            default:         return LocaleController.getString(R.string.NM_BAN_StatusUnknown);
        }
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (item == null) return;
        switch (item.id) {
            case ID_ENABLED:
                NimarkoBannerConfig.toggleEnabled();
                ctrl.setPollingEnabled(NimarkoBannerConfig.enabled);
                updateCheckState(view, NimarkoBannerConfig.enabled);
                reload();
                break;
            case ID_HIDE_AVATAR: {
                boolean nv = !ctrl.hideAvatarFlag();
                ctrl.setHideAvatarRemote(nv);
                break;
            }
            case ID_USE_AVATAR:
                NimarkoBannerConfig.setUseAvatar(!NimarkoBannerConfig.useAvatar);
                updateCheckState(view, NimarkoBannerConfig.useAvatar);
                reload();
                break;
            case ID_LITE:
                NimarkoBannerConfig.setLiteMode(!NimarkoBannerConfig.liteMode);
                updateCheckState(view, NimarkoBannerConfig.liteMode);
                reload();
                break;
            case ID_CHANGE_GLOBAL:
            case ID_SUBMIT:
                selGlobal();
                break;
            case ID_PICK_LOCAL:
                selLocal();
                break;
            case ID_DELETE_LOCAL:
                ctrl.removeLocalBanner();
                break;
            case ID_REFRESH:
                ctrl.refreshStatus();
                break;
        }
    }

    private void selGlobal() {
        String st = ctrl.statusString();
        if ("blocked".equals(st)) { err(R.string.NM_BAN_BlockedError); return; }
        if ("pending".equals(st)) { err(R.string.NM_BAN_PendingError); return; }
        pickingGlobal = true;
        openChooser();
    }

    private void selLocal() {
        if ("approved".equals(ctrl.statusString())) { err(R.string.NM_BAN_LocalNa); return; }
        pickingGlobal = false;
        openChooser();
    }

    private void openChooser() {
        try {
            Activity act = getParentActivity();
            if (act == null) return;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            if (!pickingGlobal) intent.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[]{"image/jpeg", "image/png", "image/webp", "video/mp4"});
            act.startActivityForResult(
                    Intent.createChooser(intent, LocaleController.getString(R.string.NM_BAN_PickFile)),
                    FILE_PICK_CODE);
        } catch (Throwable t) {
            android.util.Log.e("LinkiBannerUpload", "Could not open document picker", t);
            err(R.string.NM_BAN_NoAccess);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != FILE_PICK_CODE) return;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if ((data.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
            try {
                ApplicationLoader.applicationContext.getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Providers without persistent grants still allow the immediate copy below.
            }
        }
        final boolean global = pickingGlobal;
        
        Utilities.globalQueue.postRunnable(() -> processPickedFile(uri, global));
    }

    private void processPickedFile(Uri uri, boolean global) {
        File tmp = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            ctrl.ensureStarted();
            File folder = new File(ctrl.storageDir());
            if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("Banner directory is unavailable");
            tmp = File.createTempFile("temp_upload_", ".tmp", folder);
            in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
            if (in == null) throw new java.io.IOException("Document provider returned no stream");
            out = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_SIZE) {
                    out.close(); out = null;
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                    err(R.string.NM_BAN_FileTooBig);
                    return;
                }
                out.write(buf, 0, n);
            }
            out.flush(); out.close(); out = null;

            // Global submissions are validated by the server so Android need not decode every source format.
            String ext = global ? ".upload" : NimarkoBannerController.detectBannerExtension(tmp);
            if (ext == null) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                err(R.string.NM_BAN_InvalidFormat);
                return;
            }

            if (global) {
                ctrl.submitModeration(tmp, ext, total);
            } else {
                ctrl.setLocalBanner(tmp, ext);
            }
        } catch (Throwable t) {
            android.util.Log.e("LinkiBannerUpload", "Could not copy selected banner", t);
            //noinspection ResultOfMethodCallIgnored
            if (tmp != null) tmp.delete();
            err(R.string.NM_BAN_NoAccess);
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
            try { if (out != null) out.close(); } catch (Throwable ignored) {}
            try {
                ApplicationLoader.applicationContext.getContentResolver().releasePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {}
        }
    }

    private void err(int res) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                
                BulletinFactory factory = (getParentActivity() == null || getParentActivity().isFinishing() || isFinished)
                        ? BulletinFactory.global()
                        : BulletinFactory.of(this);
                factory.createSimpleBulletin(R.raw.info, LocaleController.getString(res)).show();
            } catch (Throwable ignored) {}
        });
    }

    private void reload() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }
}
