package app.nimarkogram.messenger.nfc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ProfileActivity;

import java.util.Random;

/**
 * Linki-Share: hold two phones together and each opens the other's profile.
 *
 * Both phones run this screen. Because one NFC controller cannot read and
 * emulate a card at the same time, each device alternates between the reader
 * and the card role with a slightly random period, so the roles eventually
 * complement each other and each side reads the other.
 */
public class LinkiShareActivity extends BaseFragment implements NfcAdapter.ReaderCallback {

    private static final int ROLE_READER = 0;
    private static final int ROLE_CARD = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private NfcAdapter nfcAdapter;
    private LinkiShareView heroView;
    private TextView titleView;
    private TextView subtitleView;
    private TextView enableNfcView;

    private int role = ROLE_READER;
    private int readerPhaseMs = 1400;
    private int cardPhaseMs = 1400;

    private volatile boolean resumed;
    private volatile boolean readHandled;
    private volatile boolean readerModeOn;

    @Override
    public boolean onFragmentCreate() {
        readerPhaseMs = 1200 + random.nextInt(600);
        cardPhaseMs = 1200 + random.nextInt(600);
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setTitle(LocaleController.getString(R.string.NM_LS_Title));
        actionBar.setAllowOverlayTitle(false);
        actionBar.setOccupyStatusBar(!AndroidUtilities.isTablet());

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        final FrameLayout frameLayout = (FrameLayout) fragmentView;

        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        frameLayout.addView(content, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        heroView = new LinkiShareView(context);
        heroView.setColors(
                Theme.getColor(Theme.key_actionBarDefault),
                Theme.getColor(Theme.key_windowBackgroundWhite),
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText),
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        content.addView(heroView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 230, Gravity.CENTER_HORIZONTAL));

        titleView = new TextView(context);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setText(LocaleController.getString(R.string.NM_LS_Hint));
        titleView.setPadding(AndroidUtilities.dp(28), 0, AndroidUtilities.dp(28), 0);
        content.addView(titleView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 24, 0, 0));

        subtitleView = new TextView(context);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setText(LocaleController.getString(R.string.NM_LS_HintDetail));
        subtitleView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        content.addView(subtitleView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 10, 0, 0));

        enableNfcView = new TextView(context);
        enableNfcView.setGravity(Gravity.CENTER);
        enableNfcView.setTypeface(Typeface.DEFAULT_BOLD);
        enableNfcView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        enableNfcView.setTextColor(Theme.getColor(Theme.key_actionBarDefault));
        enableNfcView.setText(LocaleController.getString(R.string.NM_LS_EnableNfc));
        enableNfcView.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
        enableNfcView.setOnClickListener(v -> openNfcSettings());
        enableNfcView.setVisibility(View.GONE);
        content.addView(enableNfcView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));

        final LinkiSharePayload self = LinkiSharePayload.current(currentAccount);
        if (self != null) {
            heroView.setSelfLabel(self.name);
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(context);
        if (nfcAdapter == null) {
            titleView.setText(LocaleController.getString(R.string.NM_LS_NoNfc));
            subtitleView.setText("");
        }
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        resumed = true;
        readHandled = false;
        if (enableNfcView == null) {
            return;
        }
        if (nfcAdapter == null) {
            nfcAdapter = NfcAdapter.getDefaultAdapter(getContext());
        }
        if (nfcAdapter == null) {
            return;
        }
        if (!nfcAdapter.isEnabled()) {
            enableNfcView.setVisibility(View.VISIBLE);
            showEnableState();
            return;
        }
        enableNfcView.setVisibility(View.GONE);
        startRoleCycle();
    }

    @Override
    public void onPause() {
        super.onPause();
        resumed = false;
        stopRoleCycle();
    }

    @Override
    public void onFragmentDestroy() {
        stopRoleCycle();
        LinkiShareNdefService.setArmed(false);
        super.onFragmentDestroy();
    }

    private void showEnableState() {
        heroView.setSuccess(false);
        titleView.setText(LocaleController.getString(R.string.NM_LS_NfcOff));
        subtitleView.setText(LocaleController.getString(R.string.NM_LS_HintDetail));
    }

    private void openNfcSettings() {
        try {
            final Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getParentActivity().startActivity(intent);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void startRoleCycle() {
        stopRoleCycle();
        role = ROLE_READER;
        handler.post(roleTick);
    }

    private void stopRoleCycle() {
        handler.removeCallbacks(roleTick);
        if (readerModeOn) {
            try {
                final Activity activity = getParentActivity();
                if (activity != null && nfcAdapter != null) {
                    nfcAdapter.disableReaderMode(activity);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            readerModeOn = false;
        }
        LinkiShareNdefService.setArmed(false);
    }

    private final Runnable roleTick = new Runnable() {
        @Override
        public void run() {
            if (!resumed || readHandled) {
                return;
            }
            if (nfcAdapter == null || !nfcAdapter.isEnabled()) {
                if (enableNfcView != null) {
                    enableNfcView.setVisibility(View.VISIBLE);
                }
                showEnableState();
                return;
            }
            applyRole(role);
            role = role == ROLE_READER ? ROLE_CARD : ROLE_READER;
            handler.postDelayed(this, role == ROLE_READER ? cardPhaseMs : readerPhaseMs);
        }
    };

    private void applyRole(int next) {
        final Activity activity = getParentActivity();
        if (activity == null || nfcAdapter == null) {
            return;
        }
        try {
            if (next == ROLE_READER) {
                // Being the card would make our own reader fail on some devices.
                LinkiShareNdefService.setArmed(false);
                if (!readerModeOn) {
                    nfcAdapter.enableReaderMode(activity, this, NfcAdapter.FLAG_READER_NFC_A, null);
                    readerModeOn = true;
                }
            } else {
                if (readerModeOn) {
                    nfcAdapter.disableReaderMode(activity);
                    readerModeOn = false;
                }
                LinkiShareNdefService.setArmed(true);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        if (readHandled) {
            return;
        }
        final LinkiSharePayload payload = readPayload(tag);
        if (payload == null) {
            return;
        }
        readHandled = true;
        AndroidUtilities.runOnUIThread(() -> onPayload(payload));
    }

    private static LinkiSharePayload readPayload(Tag tag) {
        Ndef ndef = null;
        try {
            ndef = Ndef.get(tag);
            if (ndef == null) {
                return null;
            }
            ndef.connect();
            NdefMessage message = ndef.getNdefMessage();
            if (message == null) {
                message = ndef.getCachedNdefMessage();
            }
            if (message == null) {
                return null;
            }
            for (NdefRecord record : message.getRecords()) {
                final String uri = record.toUri() == null ? null : record.toUri().toString();
                final LinkiSharePayload payload = LinkiSharePayload.fromUri(uri);
                if (payload != null) {
                    return payload;
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (ndef != null) {
                try {
                    ndef.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private void onPayload(LinkiSharePayload payload) {
        if (isScreenGone()) {
            return;
        }
        // Stop competing with the peer: from now on only act as the card, so
        // the other phone can finish reading our own profile.
        handler.removeCallbacks(roleTick);
        if (readerModeOn) {
            try {
                final Activity activity = getParentActivity();
                if (activity != null && nfcAdapter != null) {
                    nfcAdapter.disableReaderMode(activity);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            readerModeOn = false;
        }
        LinkiShareNdefService.setArmed(true);

        heroView.setPeerLabel(payload.name);
        heroView.setSuccess(true);
        titleView.setText(LocaleController.getString(R.string.NM_LS_Received));
        subtitleView.setText(payload.name.isEmpty() ? null : payload.name);
        AndroidUtilities.runOnUIThread(() -> heroView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM));

        final boolean self = payload.userId == getUserConfig().getClientUserId();
        handler.postDelayed(() -> openProfile(payload, self), 1300);
    }

    private void openProfile(LinkiSharePayload payload, boolean self) {
        if (isScreenGone()) {
            return;
        }
        if (self) {
            final Bundle args = new Bundle();
            args.putLong("user_id", getUserConfig().getClientUserId());
            presentFragment(new ProfileActivity(args));
            return;
        }
        final TLRPC.User known = getMessagesController().getUser(payload.userId);
        if (known != null) {
            showUserProfile(known.id);
            return;
        }
        if (TextUtils.isEmpty(payload.username)) {
            BulletinFactory.of(this)
                    .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.NM_LS_Unavailable))
                    .show();
            stopRoleCycle();
            readHandled = false;
            if (resumed) {
                startRoleCycle();
            }
            return;
        }
        final TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = payload.username;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_contacts_resolvedPeer) {
                final TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                getMessagesController().putUsers(res.users, false);
                getMessagesController().putChats(res.chats, false);
                for (TLRPC.User user : res.users) {
                    if (user.id == payload.userId || !res.users.isEmpty()) {
                        showUserProfile(user.id);
                        return;
                    }
                }
            }
            BulletinFactory.of(this)
                    .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.NM_LS_Unavailable))
                    .show();
        }));
    }

    private void showUserProfile(long userId) {
        final Bundle args = new Bundle();
        args.putLong("user_id", userId);
        presentFragment(new ProfileActivity(args));
    }

    private boolean isScreenGone() {
        return getParentActivity() == null || getParentActivity().isFinishing();
    }
}