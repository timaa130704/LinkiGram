package org.telegram.messenger.sessions;

import static org.telegram.messenger.LocaleController.getString;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.InputFilter;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.telegram.messenger.*;
import org.telegram.tgnet.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;

/** Fragment-owned import/export flow. Credentials never pass through logs or clipboard. */
public final class SessionTransfer {
    private static final int OPEN_SESSION = 9171, OPEN_TDATA = 9172, SAVE_SESSION = 9173;
    private final BaseFragment fragment;
    private final int account;
    private final Consumer<TLRPC.User> onLogin;
    private volatile boolean destroyed;
    private boolean busy, installed;
    private int generation, requestId, exportFormat = -1;
    private Runnable timeout;
    private AlertDialog progress;

    public SessionTransfer(BaseFragment fragment, Consumer<TLRPC.User> onLogin) {
        this.fragment = fragment; this.account = fragment.getCurrentAccount(); this.onLogin = onLogin;
    }

    public boolean isBusy() { return busy; }

    public void showImport() {
        if (busy || destroyed || onLogin == null || UserConfig.getInstance(account).isClientActivated()) return;
        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle(getString(R.string.SessionTransferLogin))
                .setItems(new CharSequence[]{getString(R.string.SessionTransferFile), getString(R.string.SessionTransferTdata),
                        "Telethon String Session", "Pyrogram String Session", "Auth Key + DC ID"}, (dialog, which) -> {
                    if (which < 2) {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
                        try { fragment.startActivityForResult(intent, which == 0 ? OPEN_SESSION : OPEN_TDATA); }
                        catch (Exception e) { error(R.string.SessionTransferFileError); }
                    } else manual(which);
                }).setNegativeButton(getString(R.string.Cancel), null).create());
    }

    private EditTextBoldCursor input(LinearLayout layout, String hint, boolean multiline) {
        EditTextBoldCursor field = new EditTextBoldCursor(fragment.getParentActivity());
        field.setTextSize(16); field.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        field.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint)); field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        field.setMaxLines(multiline ? 5 : 1);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2048)});
        layout.addView(field, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 8));
        return field;
    }

    private LinearLayout form() {
        LinearLayout l = new LinearLayout(fragment.getParentActivity()); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private void secure(AlertDialog dialog, android.content.DialogInterface.OnDismissListener cleanup) {
        dialog.setOnShowListener(d -> dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE));
        fragment.showDialog(dialog, cleanup);
    }

    private void manual(int type) {
        LinearLayout layout = form();
        EditTextBoldCursor value = input(layout, type == 4 ? "Auth Key (hex)" : "String Session", true);
        EditTextBoldCursor dc = type == 4 ? input(layout, "DC ID (1–5)", false) : null;
        if (dc != null) dc.setInputType(InputType.TYPE_CLASS_NUMBER);
        AlertDialog dialog = new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle(getString(R.string.SessionTransferLogin)).setView(layout)
                .setPositiveButton(getString(R.string.Continue), (d, w) -> {
                    try {
                        SessionData session = type == 2 ? StringSessionCodec.telethon(value.getText().toString())
                                : type == 3 ? StringSessionCodec.pyrogram(value.getText().toString())
                                : StringSessionCodec.manual(value.getText().toString(), dc.getText().toString());
                        select(Collections.singletonList(session));
                    } catch (Exception e) { error(R.string.SessionTransferInvalid); }
                }).setNegativeButton(getString(R.string.Cancel), null).create();
        secure(dialog, d -> { value.setText(""); if (dc != null) dc.setText(""); });
    }

    public boolean onActivityResult(int code, int result, Intent data) {
        if (code != OPEN_SESSION && code != OPEN_TDATA && code != SAVE_SESSION) return false;
        if (destroyed || result != Activity.RESULT_OK || data == null || data.getData() == null) { exportFormat = -1; return true; }
        Uri uri = data.getData();
        if (code == SAVE_SESSION) {
            int format = exportFormat; exportFormat = -1;
            if (format >= 0 && !busy) export(uri, format);
        } else if (code == OPEN_TDATA) {
            LinearLayout layout = form();
            EditTextBoldCursor passcode = input(layout, getString(R.string.SessionTransferPasscode), false);
            AlertDialog dialog = new AlertDialog.Builder(fragment.getParentActivity()).setTitle("tdata")
                    .setMessage(getString(R.string.SessionTransferPasscodeInfo)).setView(layout)
                    .setPositiveButton(getString(R.string.Continue), (d, w) -> read(uri, true, passcode.getText().toString()))
                    .setNegativeButton(getString(R.string.Cancel), null).create();
            secure(dialog, d -> passcode.setText(""));
        } else read(uri, false, "");
        return true;
    }

    private void read(Uri uri, boolean tdata, String passcode) {
        if (busy || destroyed) return;
        busy = true; final int token = ++generation;
        showProgress();
        Utilities.globalQueue.postRunnable(() -> {
            List<SessionData> sessions;
            try { sessions = SessionFiles.read(ApplicationLoader.applicationContext, uri, tdata, passcode); }
            catch (Exception e) { AndroidUtilities.runOnUIThread(() -> { if (valid(token)) { finish(); error(tdata ? R.string.SessionTransferTdataError : R.string.SessionTransferFileError); } }); return; }
            AndroidUtilities.runOnUIThread(() -> {
                if (!valid(token)) { for (SessionData s : sessions) s.clear(); return; }
                finish(); select(sessions);
            });
        });
    }

    private void select(List<SessionData> sessions) {
        if (destroyed) { for (SessionData s : sessions) s.clear(); return; }
        if (sessions.size() == 1) { confirm(sessions.get(0)); return; }
        CharSequence[] labels = new CharSequence[sessions.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = "ID " + sessions.get(i).userId + " · DC " + sessions.get(i).dcId;
        AlertDialog dialog = new AlertDialog.Builder(fragment.getParentActivity()).setTitle(getString(R.string.SessionTransferSelectAccount))
                .setItems(labels, (d, which) -> {
                    SessionData s = sessions.get(which);
                    confirm(new SessionData(s.dcId, s.authKey, s.userId, s.apiId));
                }).setNegativeButton(getString(R.string.Cancel), null).create();
        fragment.showDialog(dialog, d -> { for (SessionData s : sessions) s.clear(); });
    }

    private void confirm(SessionData session) {
        AlertDialog dialog = new AlertDialog.Builder(fragment.getParentActivity()).setTitle(getString(R.string.SessionTransferLogin))
                .setMessage(getString(R.string.SessionTransferImportWarning))
                .setPositiveButton(getString(R.string.Continue), (d, w) -> login(session))
                .setNegativeButton(getString(R.string.Cancel), null).create();
        fragment.showDialog(dialog, d -> session.clear());
    }

    private void login(SessionData session) {
        if (busy || destroyed || UserConfig.getInstance(account).isClientActivated()) return;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(session.authKey);
            long keyId = ByteBuffer.wrap(hash, 12, 8).order(java.nio.ByteOrder.LITTLE_ENDIAN).getLong();
            for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
                if (UserConfig.getInstance(i).isClientActivated() && ConnectionsManager.native_getCurrentAuthKeyId(i) == keyId) { error(R.string.SessionTransferAlreadyAdded); return; }
            }
        } catch (Exception e) { error(R.string.SessionTransferInvalid); return; }
        busy = true; installed = true; int token = ++generation;
        long expectedUser = session.userId;
        byte[] importKey = session.authKey.clone();
        showProgress();
        timeout = () -> { if (valid(token)) { rollback(); finish(); error(R.string.SessionTransferLoginError); } };
        AndroidUtilities.runOnUIThread(timeout, 45000);
        ConnectionsManager.native_importSession(account, session.dcId, importKey, result -> AndroidUtilities.runOnUIThread(() -> {
            if (!valid(token)) return;
            if (result == null) { installed = false; finish(); error(R.string.SessionTransferLoginError); return; }
            TLRPC.TL_users_getUsers req = new TLRPC.TL_users_getUsers(); req.id.add(new TLRPC.TL_inputUserSelf());
            requestId = ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (!valid(token)) return;
                TLRPC.User user = null;
                if (response instanceof org.telegram.tgnet.Vector) {
                    Object first = ((org.telegram.tgnet.Vector<?>) response).objects.isEmpty() ? null : ((org.telegram.tgnet.Vector<?>) response).objects.get(0);
                    if (first instanceof TLRPC.User) user = (TLRPC.User) first;
                }
                if (error != null || user == null || !user.self || user.bot || user.deleted || (expectedUser != 0 && expectedUser != user.id)) {
                    rollback(); finish(); error(R.string.SessionTransferLoginError); return;
                }
                for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) if (i != account && UserConfig.getInstance(i).getClientUserId() == user.id) {
                    rollback(); finish(); error(R.string.SessionTransferAlreadyAdded); return;
                }
                installed = false; finish(); onLogin.accept(user);
            }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
        }));
        Arrays.fill(importKey, (byte) 0);
    }

    public void showExport() {
        if (busy || destroyed || !UserConfig.getInstance(account).isClientActivated()) return;
        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity()).setTitle(getString(R.string.SessionTransferExport))
                .setItems(new CharSequence[]{"Telethon (.session)", "Pyrogram (.session)", "tdata (.zip)",
                        "Telethon String Session (.txt)", "Pyrogram String Session (.txt)", "Auth Key + DC ID (.txt)"}, (d, which) -> {
                    fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity()).setTitle(getString(R.string.SessionTransferExport))
                            .setMessage(getString(R.string.SessionTransferExportWarning))
                            .setPositiveButton(getString(R.string.Continue), (dialog, w) -> {
                                exportFormat = which;
                                String extension = which < 2 ? ".session" : which == 2 ? ".zip" : ".txt";
                                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                                        .setType(which == 2 ? "application/zip" : which < 2 ? "application/octet-stream" : "text/plain")
                                        .putExtra(Intent.EXTRA_TITLE, "telegram-session-" + UserConfig.getInstance(account).getClientUserId() + extension);
                                try { fragment.startActivityForResult(intent, SAVE_SESSION); }
                                catch (Exception e) { exportFormat = -1; error(R.string.SessionTransferFileError); }
                            }).setNegativeButton(getString(R.string.Cancel), null).create());
                }).setNegativeButton(getString(R.string.Cancel), null).create());
    }

    private void export(Uri uri, int format) {
        busy = true; int token = ++generation; showProgress();
        ConnectionsManager.native_exportSession(account, bytes -> {
            if (bytes == null) { AndroidUtilities.runOnUIThread(() -> { if (valid(token)) { finish(); exportError(null); } }); return; }
            // Native callback hands off a copy; promptly clear it after conversion.
            SessionData session;
            try {
                session = new SessionData(ByteBuffer.wrap(bytes).getInt(), Arrays.copyOfRange(bytes, 4, 260),
                        UserConfig.getInstance(account).getClientUserId(), BuildVars.APP_ID);
            } catch (Exception e) { AndroidUtilities.runOnUIThread(() -> { if (valid(token)) { finish(); exportError(e); } }); return; }
            finally { Arrays.fill(bytes, (byte) 0); }
            Utilities.globalQueue.postRunnable(() -> {
                boolean ok = false;
                Exception failure = null;
                try { if (!destroyed) { SessionFiles.write(ApplicationLoader.applicationContext, uri, format, session); ok = true; } }
                catch (Exception e) { failure = e; FileLog.e(e); }
                finally { session.clear(); }
                final boolean success = ok;
                final Exception error = failure;
                AndroidUtilities.runOnUIThread(() -> {
                    if (!valid(token)) return;
                    finish();
                    if (success) Toast.makeText(fragment.getParentActivity(), getString(R.string.SessionTransferExported), Toast.LENGTH_LONG).show();
                    else exportError(error);
                });
            });
        });
    }

    private void showProgress() {
        progress = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false); fragment.showDialog(progress);
    }
    private boolean valid(int token) { return !destroyed && busy && token == generation; }
    private void finish() {
        busy = false; requestId = 0;
        if (timeout != null) { AndroidUtilities.cancelRunOnUIThread(timeout); timeout = null; }
        if (progress != null) { progress.dismiss(); progress = null; }
    }
    private void rollback() {
        if (requestId != 0) ConnectionsManager.getInstance(account).cancelRequest(requestId, true);
        if (installed && !UserConfig.getInstance(account).isClientActivated()) ConnectionsManager.native_cleanUp(account, true);
        installed = false;
    }
    private void error(int resource) {
        if (!destroyed && fragment.getParentActivity() != null) fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle(getString(R.string.SessionTransferLogin)).setMessage(getString(resource)).setPositiveButton(getString(R.string.OK), null).create());
    }

    /** Export failures get the export title; the reason is appended so a device test is diagnosable. */
    private void exportError(Exception e) {
        if (destroyed || fragment.getParentActivity() == null) return;
        final String message = getString(R.string.SessionTransferExportError)
                + (e != null && e.getMessage() != null ? "\n\n" + e : "");
        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle(getString(R.string.SessionTransferExport)).setMessage(message)
                .setPositiveButton(getString(R.string.OK), null).create());
    }
    public void destroy() { destroyed = true; generation++; rollback(); finish(); }
}
