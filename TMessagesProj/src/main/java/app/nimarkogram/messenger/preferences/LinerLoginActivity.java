package app.nimarkogram.messenger.preferences;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import app.exteraless.player.LinerApi;
import app.exteraless.player.OnlineLyrics;

/** Uses Liner's native email/password endpoint; the password is never saved. */
public class LinerLoginActivity extends BaseFragment {
    private EditText email, password, username, displayName;
    private TextView status;
    private TextView login, logout, register;
    private boolean registrationMode;
    private boolean busy;
    private boolean disposed;
    @Override public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(R.string.LX_Liner));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(20), AndroidUtilities.dp(24), AndroidUtilities.dp(20));
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        status = new TextView(context); status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        content.addView(status, LayoutHelper.createLinear(-1, -2));
        email = new EditText(context); email.setSingleLine(true); email.setHint("Email"); email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        password = new EditText(context); password.setSingleLine(true); password.setHint(LocaleController.getString(R.string.LoginPassword));
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        for (EditText field : new EditText[]{email, password}) {
            field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            field.setSaveEnabled(false); field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            content.addView(field, LayoutHelper.createLinear(-1, 56));
        }
        username = new EditText(context); username.setSingleLine(true); username.setHint(LocaleController.getString(R.string.LX_LinerUsername));
        displayName = new EditText(context); displayName.setSingleLine(true); displayName.setHint(LocaleController.getString(R.string.LX_LinerDisplayName));
        for (EditText field : new EditText[]{username, displayName}) {
            field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            content.addView(field, LayoutHelper.createLinear(-1, 56));
        }
        login = createActionButton(context, LocaleController.getString(R.string.LX_LinerLogin), true);
        content.addView(login, LayoutHelper.createLinear(-1, 48, 0, 20, 0, 0));
        register = createActionButton(context, LocaleController.getString(R.string.LX_LinerRegister), false);
        content.addView(register, LayoutHelper.createLinear(-1, 48, 0, 8, 0, 0));
        logout = createActionButton(context, LocaleController.getString(R.string.LX_LinerLogout), false);
        content.addView(logout, LayoutHelper.createLinear(-1, 48, 0, 20, 0, 0));
        login.setOnClickListener(v -> {
            if (busy) return;
            String address = email.getText().toString().trim();
            String secret = password.getText().toString();
            if (address.isEmpty() || secret.isEmpty()) return;
            String handle = username.getText().toString().trim();
            String name = displayName.getText().toString().trim();
            if (registrationMode && (!handle.matches("[a-z0-9_]{3,30}") || name.isEmpty())) {
                status.setText(LocaleController.getString(R.string.LX_LinerRegisterInvalid));
                return;
            }
            boolean registering = registrationMode;
            busy = true; login.setEnabled(false); logout.setEnabled(false); register.setEnabled(false);
            password.setText("");
            status.setText(LocaleController.getString(R.string.Loading));
            org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                boolean success;
                boolean verificationRequired = false;
                try { if (registering) LinerApi.register(address, secret, handle, name); else LinerApi.login(address, secret); success = true; }
                catch (java.io.IOException error) { success = false; verificationRequired = error.getMessage() != null && error.getMessage().contains("verification challenge"); }
                catch (Exception ignored) { success = false; }
                final boolean ok = success;
                final boolean needsVerification = verificationRequired;
                AndroidUtilities.runOnUIThread(() -> {
                    if (disposed) return;
                    busy = false; login.setEnabled(true); logout.setEnabled(true); register.setEnabled(true);
                    if (ok) { OnlineLyrics.clearSourceCache(); updateStatus(); }
                    else status.setText(LocaleController.getString(needsVerification ? R.string.LX_LinerVerificationRequired : R.string.LX_LinerError));
                });
            });
        });
        register.setOnClickListener(v -> { if (!busy) { registrationMode = !registrationMode; updateStatus(); } });
        logout.setOnClickListener(v -> { if (!busy) { LinerApi.logout(); OnlineLyrics.clearSourceCache(); updateStatus(); } });
        updateStatus();
        fragmentView = content;
        return fragmentView;
    }
    private void updateStatus() {
        boolean signedIn = LinerApi.signedIn();
        status.setText(LocaleController.getString(signedIn ? R.string.LX_LinerReady : R.string.LX_LinerInfo));
        login.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        username.setVisibility(!signedIn && registrationMode ? View.VISIBLE : View.GONE);
        displayName.setVisibility(!signedIn && registrationMode ? View.VISIBLE : View.GONE);
        login.setText(LocaleController.getString(registrationMode ? R.string.LX_LinerRegister : R.string.LX_LinerLogin));
        register.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        register.setText(LocaleController.getString(registrationMode ? R.string.LX_LinerLogin : R.string.LX_LinerRegister));
        logout.setVisibility(signedIn ? View.VISIBLE : View.GONE);
    }

    private TextView createActionButton(Context context, String text, boolean primary) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setTextSize(14);
        button.setTypeface(AndroidUtilities.bold());
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        if (primary) {
            button.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                    AndroidUtilities.dp(8), accent,
                    Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        } else {
            button.setTextColor(accent);
            button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                    AndroidUtilities.dp(8), Color.TRANSPARENT,
                    Theme.multAlpha(accent, 0.12f)));
        }
        return button;
    }
    @Override public void onFragmentDestroy() { disposed = true; if (password != null) password.setText(""); super.onFragmentDestroy(); }
}
