package app.nimarkogram.messenger.preferences;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;
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
    private EditText email, password;
    private TextView status;
    private Button login, logout;
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
        login = new Button(context); login.setText(LocaleController.getString(R.string.LX_LinerLogin));
        content.addView(login, LayoutHelper.createLinear(-1, 52));
        logout = new Button(context); logout.setText(LocaleController.getString(R.string.LX_LinerLogout));
        content.addView(logout, LayoutHelper.createLinear(-1, 52));
        login.setOnClickListener(v -> {
            if (busy) return;
            String address = email.getText().toString().trim();
            String secret = password.getText().toString();
            if (address.isEmpty() || secret.isEmpty()) return;
            busy = true; login.setEnabled(false); logout.setEnabled(false);
            password.setText("");
            status.setText(LocaleController.getString(R.string.Loading));
            org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                boolean success;
                try { LinerApi.login(address, secret); success = true; }
                catch (Exception ignored) { success = false; }
                final boolean ok = success;
                AndroidUtilities.runOnUIThread(() -> {
                    if (disposed) return;
                    busy = false; login.setEnabled(true); logout.setEnabled(true);
                    if (ok) { OnlineLyrics.clearSourceCache(); updateStatus(); }
                    else status.setText(LocaleController.getString(R.string.LX_LinerError));
                });
            });
        });
        logout.setOnClickListener(v -> { if (!busy) { LinerApi.logout(); OnlineLyrics.clearSourceCache(); updateStatus(); } });
        updateStatus();
        fragmentView = content;
        return fragmentView;
    }
    private void updateStatus() {
        boolean signedIn = LinerApi.signedIn();
        status.setText(LocaleController.getString(signedIn ? R.string.LX_LinerReady : R.string.LX_LinerInfo));
        logout.setVisibility(signedIn ? View.VISIBLE : View.GONE);
    }
    @Override public void onFragmentDestroy() { disposed = true; if (password != null) password.setText(""); super.onFragmentDestroy(); }
}
