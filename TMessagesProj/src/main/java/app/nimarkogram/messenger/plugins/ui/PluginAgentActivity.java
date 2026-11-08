package app.nimarkogram.messenger.plugins.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.exteraless.ai.AiConfig;
import app.exteraless.ai.data.Message;
import app.exteraless.ai.data.Role;
import app.exteraless.ai.data.Service;
import app.exteraless.ai.network.Client;
import app.exteraless.ai.network.GenerationCallback;
import app.nimarkogram.messenger.plugins.Plugin;
import app.nimarkogram.messenger.plugins.PluginsController;

/**
 * Chat with an AI agent that writes LinkiGram Python plugins.
 *
 * <p>Every answer that contains a full plugin file gets an "install" action; the
 * file goes through the regular {@code showInstallDialog} path, so metadata
 * validation, updates and dependency installation stay the same as for plugins
 * imported from a file.
 *
 * <p>The "report" action attaches the saved crash report and the current source
 * of a plugin, which is what makes a broken plugin debuggable inside the chat.
 */
public class PluginAgentActivity extends BaseFragment {

    private static final int MENU_REPORT = 1;
    private static final int MENU_SETTINGS = 2;
    /** Upper bound of the in-memory conversation that is resent with every request. */
    private static final int MAX_HISTORY = 16;
    private static final int MAX_HISTORY_CHARS = 120000;
    private static final Pattern FENCE = Pattern.compile("```[a-zA-Z0-9_+.-]*\\n?");

    /** Kept for the app session so closing and reopening the screen keeps the context. */
    private static final ArrayList<Message> SESSION_HISTORY = new ArrayList<>();

    private final String pendingPluginId;
    private Client client;
    private String requestId;
    private boolean busy;

    private ScrollView scrollView;
    private LinearLayout messagesView;
    private EditText inputField;
    private View sendButton;
    private LinearLayout inputPanel;
    private Context context;
    private String modelLabel;
    private int bottomInset;

    public PluginAgentActivity() {
        this(null);
    }

    public PluginAgentActivity(String pluginId) {
        super();
        this.pendingPluginId = pluginId;
    }

    @Override
    public View createView(Context context) {
        this.context = context;
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.NM_PluginAgentTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_REPORT) {
                    showReportPicker(fragmentView);
                } else if (id == MENU_SETTINGS) {
                    presentFragment(new app.exteraless.ai.ui.AiSettingsActivity());
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem reportItem = menu.addItem(MENU_REPORT, R.drawable.msg_report);
        reportItem.setContentDescription(LocaleController.getString(R.string.NM_PluginAgentReport));
        ActionBarMenuItem settingsItem = menu.addItem(MENU_SETTINGS, R.drawable.msg_settings);
        settingsItem.setContentDescription(LocaleController.getString(R.string.Settings));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        ThemeBind.backgroundColor(root, Theme.key_windowBackgroundWhite);

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        root.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        messagesView = new LinearLayout(context);
        messagesView.setOrientation(LinearLayout.VERTICAL);
        messagesView.setPadding(dp(12), dp(10), dp(12), dp(10));
        scrollView.addView(messagesView, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        inputPanel = new LinearLayout(context);
        inputPanel.setOrientation(LinearLayout.HORIZONTAL);
        inputPanel.setGravity(Gravity.CENTER_VERTICAL);
        inputPanel.setPadding(dp(8), dp(8), dp(8), dp(8) + bottomInset);
        ThemeBind.backgroundColor(inputPanel, Theme.key_windowBackgroundWhite);
        root.addView(inputPanel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        inputField = new EditTextBoldCursor(context);
        inputField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        inputField.setHint(LocaleController.getString(R.string.NM_PluginAgentInputHint));
        inputField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        inputField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        inputField.setBackground(Theme.createRoundRectDrawable(dp(20), Theme.getColor(Theme.key_chat_messagePanelBackground)));
        inputField.setPadding(dp(14), dp(9), dp(14), dp(9));
        inputField.setMaxLines(5);
        inputField.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        inputField.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        inputField.setMinHeight(dp(40));
        inputField.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submit();
                return true;
            }
            return false;
        });
        inputPanel.addView(inputField, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        FrameLayout sendContainer = new FrameLayout(context);
        sendContainer.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(40),
                Theme.getColor(Theme.key_chats_actionBackground),
                Theme.getColor(Theme.key_chats_actionPressedBackground)));
        ImageView sendIcon = new ImageView(context);
        sendIcon.setScaleType(ImageView.ScaleType.CENTER);
        sendIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_send).mutate());
        sendIcon.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        sendContainer.addView(sendIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        sendContainer.setOnClickListener(view -> submit());
        sendContainer.setContentDescription(LocaleController.getString(R.string.NM_PluginAgentSend));
        sendButton = sendContainer;
        inputPanel.addView(sendContainer, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 8, 0, 2, 0));

        fragmentView = root;
        replaySession();
        AndroidUtilities.runOnUIThread(this::reportPendingPlugin);
        return fragmentView;
    }

    private void replaySession() {
        if (!SESSION_HISTORY.isEmpty()) {
            for (Message message : SESSION_HISTORY) {
                boolean user = "user".equals(message.role());
                addBubble(format(message.content()), user, user ? null : PluginAgentPrompt.extractCode(message.content()));
            }
        } else {
            addBubble(LocaleController.getString(R.string.NM_PluginAgentHint), false, null);
        }
        scrollToBottom();
    }

    private void reportPendingPlugin() {
        if (pendingPluginId == null || fragmentView == null) {
            return;
        }
        Plugin plugin = PluginsController.getInstance().plugins.get(pendingPluginId);
        if (plugin != null) {
            reportPlugin(plugin);
        }
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        bottomInset = bottom;
        if (inputPanel != null) {
            inputPanel.setPadding(dp(8), dp(8), dp(8), dp(8) + bottom);
        }
    }

    @Override
    public void onFragmentDestroy() {
        if (client != null && requestId != null) {
            client.cancel(requestId);
        }
        client = null;
        requestId = null;
        busy = false;
        super.onFragmentDestroy();
    }

    private void submit() {
        if (busy || inputField == null) {
            return;
        }
        String text = inputField.getText() == null ? "" : inputField.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            return;
        }
        inputField.setText("");
        send(text);
    }

    private void send(String text) {
        if (busy) {
            return;
        }
        addBubble(format(text), true, null);
        remember(new Message("user", text));
        scrollToBottom();

        Service service = AiConfig.getSelectedService();
        if (service == null || !service.hasCredentials()) {
            addBubble(LocaleController.getString(R.string.NM_PluginAgentConfigure), false, null);
            return;
        }
        modelLabel = service.getShortModel();
        actionBar.setSubtitle(modelLabel);

        Role role = new Role("PluginAgent",
                PluginAgentPrompt.SYSTEM + "\n\n" + PluginAgentPrompt.catalog());
        client = new Client.Builder()
                .roleOverride(role)
                .serviceOverride(service)
                .build();
        requestId = UUID.randomUUID().toString();
        setBusy(true);

        final StringBuilder chunked = new StringBuilder();
        client.generate(requestId, new ArrayList<>(SESSION_HISTORY), false, new GenerationCallback() {
            @Override
            public void onChunk(String chunk) {
                chunked.append(chunk);
            }

            @Override
            public void onResponse(String response) {
                setBusy(false);
                String answer = TextUtils.isEmpty(response) ? chunked.toString() : response;
                if (TextUtils.isEmpty(answer)) {
                    answer = LocaleController.getString(R.string.OEAiFailed);
                }
                remember(new Message("assistant", answer));
                if (fragmentView != null) {
                    addBubble(format(answer), false, PluginAgentPrompt.extractCode(answer));
                    scrollToBottom();
                }
            }

            @Override
            public void onError(int code, String message) {
                setBusy(false);
                String detail = TextUtils.isEmpty(message)
                        ? LocaleController.getString(R.string.OEAiFailed)
                        : message;
                if (fragmentView != null) {
                    addError(code == 0 ? detail : detail + " · " + code);
                    scrollToBottom();
                }
            }
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        if (sendButton != null) {
            sendButton.setEnabled(!value);
            sendButton.setAlpha(value ? 0.5f : 1f);
        }
        if (actionBar != null) {
            actionBar.setSubtitle(value ? "…" : modelLabel);
        }
    }

    /** Keeps the in-memory conversation bounded; only the last turns are resent. */
    private static void remember(Message message) {
        SESSION_HISTORY.add(message);
        int chars = 0;
        int keepFrom = 0;
        for (int a = SESSION_HISTORY.size() - 1; a >= 0; a--) {
            String content = SESSION_HISTORY.get(a).content();
            chars += content == null ? 0 : content.length();
            boolean tooOld = SESSION_HISTORY.size() - a > MAX_HISTORY
                    || (chars > MAX_HISTORY_CHARS && a < SESSION_HISTORY.size() - 1);
            if (tooOld) {
                keepFrom = a + 1;
                break;
            }
        }
        if (keepFrom > 0) {
            SESSION_HISTORY.subList(0, keepFrom).clear();
        }
    }

    private void addError(String text) {
        TextView view = createTextBubble(text, false);
        view.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        messagesView.addView(view, bubbleParams(false));
        scrollToBottom();
    }

    private void addBubble(CharSequence text, boolean user, String code) {
        TextView view = createTextBubble(text, user);
        messagesView.addView(view, bubbleParams(user));
        if (!user && !TextUtils.isEmpty(code)) {
            final String pluginCode = code;
            TextView install = new TextView(context);
            install.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            install.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            install.setText(LocaleController.getString(R.string.NM_PluginAgentInstall));
            install.setPadding(dp(12), dp(8), dp(12), dp(8));
            install.setOnClickListener(v -> installPlugin(pluginCode));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.START;
            lp.leftMargin = dp(4);
            lp.bottomMargin = dp(10);
            messagesView.addView(install, lp);
        }
    }

    private TextView createTextBubble(CharSequence text, boolean user) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        view.setTextIsSelectable(true);
        view.setText(text);
        if (user) {
            view.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText));
            view.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.getColor(Theme.key_chat_messagePanelBackground)));
            view.setPadding(dp(12), dp(9), dp(12), dp(9));
        } else {
            view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            view.setPadding(dp(4), dp(4), dp(4), dp(6));
        }
        return view;
    }

    private LinearLayout.LayoutParams bubbleParams(boolean user) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = user ? Gravity.END : Gravity.START;
        lp.leftMargin = user ? dp(40) : 0;
        lp.rightMargin = user ? 0 : dp(24);
        lp.bottomMargin = dp(8);
        return lp;
    }

    private void scrollToBottom() {
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    /** Turns fenced code into monospace spans; the rest stays plain text. */
    private CharSequence format(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "";
        }
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] parts = raw.split("```", -1);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (i % 2 == 1) {
                Matcher fence = FENCE.matcher(part);
                if (fence.lookingAt() && fence.end() <= 16) {
                    part = part.substring(fence.end());
                }
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
                int start = out.length();
                out.append(part.trim());
                out.setSpan(new TypefaceSpan("monospace"), start, out.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.append('\n');
            } else {
                out.append(part);
            }
        }
        return out;
    }

    private void showReportPicker(View anchor) {
        List<Plugin> plugins = new ArrayList<>(PluginsController.getInstance().plugins.values());
        if (plugins.isEmpty()) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                    LocaleController.getString(R.string.NM_PluginAgentNoPlugins)).show();
            return;
        }
        plugins.sort(Comparator
                .comparing((Plugin plugin) -> !plugin.hasError())
                .thenComparing(plugin -> plugin.getName() == null ? "" : plugin.getName()));
        ItemOptions options = ItemOptions.makeOptions(this, anchor);
        for (Plugin plugin : plugins) {
            String name = TextUtils.isEmpty(plugin.getName()) ? plugin.getId() : plugin.getName();
            String title = name + (plugin.hasError() ? " ⚠" : "");
            options.add(R.drawable.msg_report, title, () -> reportPlugin(plugin));
        }
        options.show();
    }

    private void reportPlugin(Plugin plugin) {
        if (plugin == null || busy) {
            return;
        }
        final String pluginId = plugin.getId();
        final String pluginName = plugin.getName();
        Utilities.globalQueue.postRunnable(() -> {
            String report;
            try {
                Plugin fresh = PluginsController.getInstance().plugins.get(pluginId);
                report = PluginAgentPrompt.report(fresh == null ? plugin : fresh);
            } catch (Throwable t) {
                FileLog.e("PluginAgent: cannot build report", t);
                report = String.valueOf(t);
            }
            final String detail = report;
            AndroidUtilities.runOnUIThread(() -> {
                if (fragmentView == null) {
                    return;
                }
                String prompt = LocaleController.formatString(R.string.NM_PluginAgentReportPrompt, pluginName);
                send(prompt + "\n\n" + detail);
            });
        });
    }

    private void installPlugin(String code) {
        final String id = PluginAgentPrompt.pluginIdOf(code);
        if (TextUtils.isEmpty(id)) {
            BulletinFactory.of(this).createErrorBulletin(
                    LocaleController.getString(R.string.NM_PluginAgentBadId)).show();
            return;
        }
        final File directory = new File(ApplicationLoader.getFilesDirFixed(), "plugin-agent");
        final File target = new File(directory, id + ".plugin");
        Utilities.globalQueue.postRunnable(() -> {
            boolean written = true;
            try {
                if (!directory.isDirectory() && !directory.mkdirs()) {
                    written = false;
                } else {
                    try (FileOutputStream output = new FileOutputStream(target)) {
                        output.write(code.getBytes("UTF-8"));
                    }
                }
            } catch (Throwable t) {
                FileLog.e("PluginAgent: cannot save plugin", t);
                written = false;
            }
            final boolean ok = written;
            AndroidUtilities.runOnUIThread(() -> {
                if (fragmentView == null) {
                    return;
                }
                if (!ok) {
                    BulletinFactory.of(this).createErrorBulletin(
                            LocaleController.getString(R.string.NM_AddPluginFailed)).show();
                    return;
                }
                try {
                    PluginsController.getInstance().showInstallDialog(
                            PluginAgentActivity.this, target.getAbsolutePath(), false);
                } catch (Throwable t) {
                    FileLog.e("PluginAgent: cannot show install dialog", t);
                    BulletinFactory.of(this).createErrorBulletin(
                            LocaleController.getString(R.string.NM_AddPluginFailed)).show();
                }
            });
        });
    }

    private static int dp(float value) {
        return AndroidUtilities.dp(value);
    }
}