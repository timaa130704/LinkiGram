/**
 * This file is part of LinkiGram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * LinkiGram modifications:
 * Copyright Ettacent, 2026.
 *
 * AyuMoments settings screen (port of exteraless OpenExteraAyuMomentsActivity,
 * Copyright github.com/exteraless / Ayugram team, GPL v.2 or later).
 */

package app.nimarkogram.messenger.preferences;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import app.nimarkogram.messenger.NimarkoConfig;
import app.nimarkogram.messenger.preferences.helpers.PopupHelper;
import app.nimarkogram.messenger.preferences.helpers.SettingsHelper;

public class AyuMomentsPreferencesActivity extends BasePreferencesActivity {

    private static final int ROW_SAVE_DELETED_MASTER     = 100;
    private static final int ROW_SAVE_DELETED_PRIVATE    = 101;
    private static final int ROW_SAVE_DELETED_GROUPS     = 102;
    private static final int ROW_SAVE_DELETED_CHANNELS   = 103;
    private static final int ROW_SAVE_DELETED_BOT_USER   = 104;
    private static final int ROW_SAVE_DELETED_BOT_CHAT   = 105;
    private static final int ROW_SAVE_IN_ARCHIVED        = 106;

    private static final int ROW_SAVE_EDITS              = 110;

    private static final int ROW_SAVE_MEDIA_MASTER       = 120;
    private static final int ROW_SAVE_MEDIA_PRIVATE      = 121;
    private static final int ROW_SAVE_MEDIA_PUB_CHANNELS = 122;
    private static final int ROW_SAVE_MEDIA_PRIV_CHANNELS = 123;
    private static final int ROW_SAVE_MEDIA_PUB_GROUPS   = 124;
    private static final int ROW_SAVE_MEDIA_PRIV_GROUPS  = 125;

    private static final int ROW_SAVE_LAST_SEEN          = 130;
    private static final int ROW_SAVE_READ_DATE          = 131;

    private static final int ROW_TRANSLUCENT_DELETED     = 140;

    private static final int ROW_DB_EXPORT               = 150;
    private static final int ROW_DB_IMPORT               = 151;
    private static final int ROW_DB_CLEAR                = 152;

    // Ghost mode (moved here from "General")
    private static final int ROW_GHOST_PRESET        = 160;
    private static final int ROW_GHOST_MASTER        = 161;
    private static final int ROW_GHOST_TYPING        = 162;
    private static final int ROW_GHOST_ONLINE        = 163;
    private static final int ROW_GHOST_READ          = 164;
    private static final int ROW_GHOST_STORIES       = 165;
    private static final int ROW_GHOST_READ_AFTER    = 166;
    private static final int ROW_GHOST_OFFLINE_AFTER = 167;
    private static final int ROW_LAST_SEEN_CACHE     = 168;

    private static final int DATABASE_IMPORT_REQUEST_CODE = 6110;

    @Override
    public String getTitle() {
        return LocaleController.getString(R.string.NM_AyuMoments);
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        // ===== Ghost mode =====
        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_GhostMode)));
        items.add(UItem.asButton(ROW_GHOST_PRESET,
                LocaleController.getString(R.string.NM_PrivacyPreset),
                getPrivacyPresetValue()));
        items.add(SettingsHelper.asSwitchCG(ROW_GHOST_MASTER,
                        LocaleController.getString(R.string.NM_GhostMaster),
                        LocaleController.getString(R.string.NM_GhostMaster_Desc))
                .setChecked(NimarkoConfig.isGhostModeActive()));
        items.add(SettingsHelper.asSwitchCG(ROW_GHOST_TYPING,
                        LocaleController.getString(R.string.NM_GhostTyping),
                        LocaleController.getString(R.string.NM_GhostTyping_Desc))
                .setChecked(NimarkoConfig.ghostTyping));
        items.add(SettingsHelper.asSwitchCG(ROW_GHOST_ONLINE,
                        LocaleController.getString(R.string.NM_GhostOnline),
                        LocaleController.getString(R.string.NM_GhostOnline_Desc))
                .setChecked(NimarkoConfig.ghostOnline));
        items.add(SettingsHelper.asSwitchCG(ROW_GHOST_READ,
                        LocaleController.getString(R.string.NM_GhostReadReceipts),
                        LocaleController.getString(R.string.NM_GhostReadReceipts_Desc))
                .setChecked(NimarkoConfig.ghostReadReceipts));
        items.add(SettingsHelper.asSwitchCG(ROW_GHOST_STORIES,
                        LocaleController.getString(R.string.NM_GhostStories),
                        LocaleController.getString(R.string.NM_GhostStories_Desc))
                .setChecked(NimarkoConfig.ghostStories));
        items.add(SettingsHelper.asSwitchCG(ROW_GHOST_READ_AFTER,
                        LocaleController.getString(R.string.NM_GhostReadAfterSend),
                        LocaleController.getString(R.string.NM_GhostReadAfterSend_Desc))
                .setChecked(NimarkoConfig.ghostReadAfterSend));
        items.add(SettingsHelper.asSwitchCG(ROW_GHOST_OFFLINE_AFTER,
                        LocaleController.getString(R.string.NM_GhostOfflineAfterSend),
                        LocaleController.getString(R.string.NM_GhostOfflineAfterSend_Desc))
                .setChecked(NimarkoConfig.ghostOfflineAfterSend));
        items.add(UItem.asShadow(null));

        // ===== Saving of deleted messages =====
        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_Ayu_HeaderSaving)));
        items.add(SettingsHelper.asSwitchCG(ROW_SAVE_DELETED_MASTER,
                        LocaleController.getString(R.string.NM_SaveDeletedMessages),
                        LocaleController.getString(R.string.NM_SaveDeletedMessages_Desc))
                .setChecked(NimarkoConfig.saveDeletedMessages));

        if (NimarkoConfig.saveDeletedMessages) {
            items.add(SettingsHelper.asSwitchCG(ROW_SAVE_DELETED_PRIVATE,
                            LocaleController.getString(R.string.NM_Ayu_SaveDeleted_Private))
                    .setChecked(NimarkoConfig.saveDeletedInPrivateChats));
            items.add(SettingsHelper.asSwitchCG(ROW_SAVE_DELETED_GROUPS,
                            LocaleController.getString(R.string.NM_Ayu_SaveDeleted_Groups))
                    .setChecked(NimarkoConfig.saveDeletedInGroups));
            items.add(SettingsHelper.asSwitchCG(ROW_SAVE_DELETED_CHANNELS,
                            LocaleController.getString(R.string.NM_Ayu_SaveDeleted_Channels))
                    .setChecked(NimarkoConfig.saveDeletedInChannels));
            items.add(SettingsHelper.asSwitchCG(ROW_SAVE_DELETED_BOT_USER,
                            LocaleController.getString(R.string.NM_Ayu_SaveDeleted_BotUser))
                    .setChecked(NimarkoConfig.saveDeletedMessageForBotUser));
            items.add(SettingsHelper.asSwitchCG(ROW_SAVE_DELETED_BOT_CHAT,
                            LocaleController.getString(R.string.NM_Ayu_SaveDeleted_BotChat))
                    .setChecked(NimarkoConfig.saveDeletedMessageForBot));
            items.add(SettingsHelper.asSwitchCG(ROW_SAVE_IN_ARCHIVED,
                            LocaleController.getString(R.string.NM_Ayu_SaveInArchived))
                    .setChecked(NimarkoConfig.saveInArchivedChats));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.NM_Ayu_ShadowSaving)));

        // ===== Edit history =====
        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_Ayu_HeaderEdits)));
        items.add(SettingsHelper.asSwitchCG(ROW_SAVE_EDITS,
                        LocaleController.getString(R.string.NM_Ayu_SaveEdits),
                        LocaleController.getString(R.string.NM_Ayu_SaveEdits_Desc))
                .setChecked(NimarkoConfig.enableSaveEditsHistory));
        items.add(UItem.asShadow(LocaleController.getString(R.string.NM_Ayu_ShadowEdits)));

        // ===== Media saving =====
        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_Ayu_HeaderMedia)));
        items.add(SettingsHelper.asSwitchCG(ROW_SAVE_MEDIA_MASTER,
                        LocaleController.getString(R.string.NM_Ayu_SaveMedia),
                        LocaleController.getString(R.string.NM_Ayu_SaveMedia_Desc))
                .setChecked(NimarkoConfig.saveMediaFiles));
        if (NimarkoConfig.saveMediaFiles) {
            items.add(SettingsHelper.asSwitchCG(ROW_SAVE_MEDIA_PRIVATE,
                            LocaleController.getString(R.string.NM_Ayu_SaveMedia_Private))
                    .setChecked(NimarkoConfig.saveMediaInPrivateChats));
            items.add(SettingsHelper.asSwitchCG(ROW_SAVE_MEDIA_PUB_CHANNELS,
                            LocaleController.getString(R.string.NM_Ayu_SaveMedia_PubChannels))
                    .setChecked(NimarkoConfig.saveMediaInPublicChannels));
            items.add(SettingsHelper.asSwitchCG(ROW_SAVE_MEDIA_PRIV_CHANNELS,
                            LocaleController.getString(R.string.NM_Ayu_SaveMedia_PrivChannels))
                    .setChecked(NimarkoConfig.saveMediaInPrivateChannels));
            items.add(SettingsHelper.asSwitchCG(ROW_SAVE_MEDIA_PUB_GROUPS,
                            LocaleController.getString(R.string.NM_Ayu_SaveMedia_PubGroups))
                    .setChecked(NimarkoConfig.saveMediaInPublicGroups));
            items.add(SettingsHelper.asSwitchCG(ROW_SAVE_MEDIA_PRIV_GROUPS,
                            LocaleController.getString(R.string.NM_Ayu_SaveMedia_PrivGroups))
                    .setChecked(NimarkoConfig.saveMediaInPrivateGroups));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.NM_Ayu_ShadowMedia)));

        // ===== Local tracking =====
        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_Ayu_HeaderSpy)));
        items.add(SettingsHelper.asSwitchCG(ROW_SAVE_LAST_SEEN,
                        LocaleController.getString(R.string.NM_Ayu_SaveLastSeen),
                        LocaleController.getString(R.string.NM_Ayu_SaveLastSeen_Desc))
                .setChecked(NimarkoConfig.saveLocalLastSeen));
        items.add(SettingsHelper.asSwitchCG(ROW_SAVE_READ_DATE,
                        LocaleController.getString(R.string.NM_Ayu_SaveReadDate),
                        LocaleController.getString(R.string.NM_Ayu_SaveReadDate_Desc))
                .setChecked(NimarkoConfig.saveReadDate));
        items.add(SettingsHelper.asSwitchCG(ROW_LAST_SEEN_CACHE,
                        LocaleController.getString(R.string.NM_LastSeenCache),
                        LocaleController.getString(R.string.NM_LastSeenCache_Desc))
                .setChecked(NimarkoConfig.lastSeenCacheEnabled));
        items.add(UItem.asShadow(LocaleController.getString(R.string.NM_Ayu_ShadowSpy)));

        // ===== Deleted messages view =====
        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_Ayu_HeaderView)));
        items.add(SettingsHelper.asSwitchCG(ROW_TRANSLUCENT_DELETED,
                        LocaleController.getString(R.string.NM_Ayu_Translucent),
                        LocaleController.getString(R.string.NM_Ayu_Translucent_Desc))
                .setChecked(NimarkoConfig.translucentDeletedMessages));
        items.add(UItem.asShadow(LocaleController.getString(R.string.NM_Ayu_ShadowView)));

        // ===== Database =====
        items.add(UItem.asHeader(LocaleController.getString(R.string.NM_Ayu_HeaderDb)));
        items.add(UItem.asButton(ROW_DB_EXPORT,
                R.drawable.msg_archive_solar,
                LocaleController.getString(R.string.NM_Ayu_DbExport)));
        items.add(UItem.asButton(ROW_DB_IMPORT,
                R.drawable.msg_download_solar,
                LocaleController.getString(R.string.NM_Ayu_DbImport)));
        items.add(UItem.asButton(ROW_DB_CLEAR,
                R.drawable.msg_clear_solar,
                LocaleController.getString(R.string.NM_Ayu_DbClear)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.NM_Ayu_ShadowDb)));
    }

    @Override
    public void onClick(UItem uItem, View view, int position, float x, float y) {
        if (uItem == null) return;
        switch (uItem.id) {
            case ROW_GHOST_PRESET: {
                ArrayList<String> presets = new ArrayList<>();
                presets.add(LocaleController.getString(R.string.NM_PresetNormal));
                presets.add(LocaleController.getString(R.string.NM_PresetGhost));
                presets.add(LocaleController.getString(R.string.NM_PresetParanoia));
                PopupHelper.show(presets, LocaleController.getString(R.string.NM_PrivacyPreset), NimarkoConfig.privacyPreset, getContext(), i -> {
                    NimarkoConfig.applyPrivacyPreset(i);
                    if (listView != null && listView.adapter != null) {
                        listView.adapter.update(true);
                    }
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info, presets.get(i)).show();
                });
                break;
            }
            case ROW_GHOST_MASTER:
                NimarkoConfig.setGhostMode(!NimarkoConfig.isGhostModeActive());
                reloadMainInfo();
                break;
            case ROW_GHOST_TYPING:
                NimarkoConfig.toggleGhostTyping();
                updateCheckState(view, NimarkoConfig.ghostTyping);
                break;
            case ROW_GHOST_ONLINE:
                NimarkoConfig.toggleGhostOnline();
                updateCheckState(view, NimarkoConfig.ghostOnline);
                break;
            case ROW_GHOST_READ:
                NimarkoConfig.toggleGhostReadReceipts();
                updateCheckState(view, NimarkoConfig.ghostReadReceipts);
                break;
            case ROW_GHOST_STORIES:
                NimarkoConfig.toggleGhostStories();
                updateCheckState(view, NimarkoConfig.ghostStories);
                break;
            case ROW_GHOST_READ_AFTER:
                NimarkoConfig.toggleGhostReadAfterSend();
                updateCheckState(view, NimarkoConfig.ghostReadAfterSend);
                break;
            case ROW_GHOST_OFFLINE_AFTER:
                NimarkoConfig.toggleGhostOfflineAfterSend();
                updateCheckState(view, NimarkoConfig.ghostOfflineAfterSend);
                break;
            case ROW_LAST_SEEN_CACHE:
                NimarkoConfig.toggleLastSeenCacheEnabled();
                updateCheckState(view, NimarkoConfig.lastSeenCacheEnabled);
                break;
            case ROW_SAVE_DELETED_MASTER:
                NimarkoConfig.toggleSaveDeletedMessages();
                updateCheckState(view, NimarkoConfig.saveDeletedMessages);
                reloadMainInfo();
                break;
            case ROW_SAVE_DELETED_PRIVATE:
                NimarkoConfig.toggleSaveDeletedInPrivateChats();
                updateCheckState(view, NimarkoConfig.saveDeletedInPrivateChats);
                break;
            case ROW_SAVE_DELETED_GROUPS:
                NimarkoConfig.toggleSaveDeletedInGroups();
                updateCheckState(view, NimarkoConfig.saveDeletedInGroups);
                break;
            case ROW_SAVE_DELETED_CHANNELS:
                NimarkoConfig.toggleSaveDeletedInChannels();
                updateCheckState(view, NimarkoConfig.saveDeletedInChannels);
                break;
            case ROW_SAVE_DELETED_BOT_USER:
                NimarkoConfig.toggleSaveDeletedMessageForBotUser();
                updateCheckState(view, NimarkoConfig.saveDeletedMessageForBotUser);
                break;
            case ROW_SAVE_DELETED_BOT_CHAT:
                NimarkoConfig.toggleSaveDeletedMessageForBot();
                updateCheckState(view, NimarkoConfig.saveDeletedMessageForBot);
                break;
            case ROW_SAVE_IN_ARCHIVED:
                NimarkoConfig.toggleSaveInArchivedChats();
                updateCheckState(view, NimarkoConfig.saveInArchivedChats);
                break;
            case ROW_SAVE_EDITS:
                NimarkoConfig.toggleEnableSaveEditsHistory();
                updateCheckState(view, NimarkoConfig.enableSaveEditsHistory);
                break;
            case ROW_SAVE_MEDIA_MASTER:
                NimarkoConfig.toggleSaveMediaFiles();
                updateCheckState(view, NimarkoConfig.saveMediaFiles);
                reloadMainInfo();
                break;
            case ROW_SAVE_MEDIA_PRIVATE:
                NimarkoConfig.toggleSaveMediaInPrivateChats();
                updateCheckState(view, NimarkoConfig.saveMediaInPrivateChats);
                break;
            case ROW_SAVE_MEDIA_PUB_CHANNELS:
                NimarkoConfig.toggleSaveMediaInPublicChannels();
                updateCheckState(view, NimarkoConfig.saveMediaInPublicChannels);
                break;
            case ROW_SAVE_MEDIA_PRIV_CHANNELS:
                NimarkoConfig.toggleSaveMediaInPrivateChannels();
                updateCheckState(view, NimarkoConfig.saveMediaInPrivateChannels);
                break;
            case ROW_SAVE_MEDIA_PUB_GROUPS:
                NimarkoConfig.toggleSaveMediaInPublicGroups();
                updateCheckState(view, NimarkoConfig.saveMediaInPublicGroups);
                break;
            case ROW_SAVE_MEDIA_PRIV_GROUPS:
                NimarkoConfig.toggleSaveMediaInPrivateGroups();
                updateCheckState(view, NimarkoConfig.saveMediaInPrivateGroups);
                break;
            case ROW_SAVE_LAST_SEEN:
                NimarkoConfig.toggleSaveLocalLastSeen();
                updateCheckState(view, NimarkoConfig.saveLocalLastSeen);
                break;
            case ROW_SAVE_READ_DATE:
                NimarkoConfig.toggleSaveReadDate();
                updateCheckState(view, NimarkoConfig.saveReadDate);
                break;
            case ROW_TRANSLUCENT_DELETED:
                NimarkoConfig.toggleTranslucentDeletedMessages();
                updateCheckState(view, NimarkoConfig.translucentDeletedMessages);
                break;
            case ROW_DB_EXPORT:
                com.radolyn.ayugram.database.AyuData.exportAyuDatabase(this);
                break;
            case ROW_DB_IMPORT:
                openDatabasePicker();
                break;
            case ROW_DB_CLEAR:
                showClearAyuDatabaseDialog();
                break;
        }
    }

    private void openDatabasePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, DATABASE_IMPORT_REQUEST_CODE);
        } catch (Exception e) {
            FileLog.e(e);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.ErrorOccurred)).show();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode != DATABASE_IMPORT_REQUEST_CODE) {
            super.onActivityResultFragment(requestCode, resultCode, data);
            return;
        }
        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            importDatabaseFromUri(data.getData());
        }
    }

    private void importDatabaseFromUri(Uri uri) {
        File tempFile = new File(AndroidUtilities.getCacheDir(), "ayu_import.db");
        Utilities.globalQueue.postRunnable(() -> {
            try {
                try (InputStream is = getParentActivity().getContentResolver().openInputStream(uri);
                     FileOutputStream os = new FileOutputStream(tempFile)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        os.write(buf, 0, n);
                    }
                }
                AndroidUtilities.runOnUIThread(() ->
                        com.radolyn.ayugram.database.AyuData.importAyuDatabase(this, tempFile));
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    if (getParentActivity() != null) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.ErrorOccurred)).show();
                    }
                });
            }
        });
    }

    private void showClearAyuDatabaseDialog() {
        if (getParentActivity() == null) {
            return;
        }
        new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.NM_Ayu_DbClear))
                .setMessage(LocaleController.getString(R.string.AreYouSure))
                .setPositiveButton(LocaleController.getString(R.string.Clear), (dialog, which) -> {
                    AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                    progressDialog.setCanCancel(false);
                    progressDialog.show();
                    Utilities.globalQueue.postRunnable(() -> {
                        try {
                            com.radolyn.ayugram.messages.AyuMessagesController.getInstance().clean();
                        } catch (Throwable t) {
                            FileLog.e("nimarko: ayu clean failed", t);
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            progressDialog.dismiss();
                            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, LocaleController.getString(R.string.NM_Ayu_DbCleared)).show();
                        });
                    });
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), (d, w) -> d.dismiss())
                .show();
    }

    private String getPrivacyPresetValue() {
        switch (NimarkoConfig.privacyPreset) {
            case NimarkoConfig.PRESET_GHOST:
                return LocaleController.getString(R.string.NM_PresetGhost);
            case NimarkoConfig.PRESET_PARANOIA:
                return LocaleController.getString(R.string.NM_PresetParanoia);
            default:
                return LocaleController.getString(R.string.NM_PresetNormal);
        }
    }

    private void reloadMainInfo() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }
}
