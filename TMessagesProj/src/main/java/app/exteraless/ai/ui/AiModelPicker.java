package app.exteraless.ai.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import app.exteraless.ai.data.ModelInfo;
import app.exteraless.ai.data.RemoteModel;
import app.exteraless.ai.network.Client;
import app.exteraless.ai.network.ModelsCatalog;

public class AiModelPicker extends BottomSheet {

    private final Utilities.Callback<String> onPick;
    private final String currentModel;

    private final RecyclerListView listView;
    private final RadialProgressView progressView;
    private final TextView emptyView;
    private final Adapter adapter;

    private final ArrayList<RemoteModel> models = new ArrayList<>();
    private final ArrayList<RemoteModel> filtered = new ArrayList<>();
    private String query = "";
    private String typed = "";
    private RemoteModel customModel;
    private String loadError;

    public static void show(Context context, Theme.ResourcesProvider resourcesProvider,
                            String currentModel, Utilities.Callback<String> onPick) {
        if (context == null) {
            return;
        }
        new AiModelPicker(context, resourcesProvider, currentModel, onPick).show();
    }

    private AiModelPicker(Context context, Theme.ResourcesProvider resourcesProvider,
                          String currentModel, Utilities.Callback<String> onPick) {
        super(context, true, resourcesProvider);
        setCanDismissWithSwipe(false);
        this.onPick = onPick;
        this.currentModel = currentModel;

        FrameLayout container = new FrameLayout(context);

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        titleView.setText(getString(R.string.OEAiModel));
        container.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 22, 14, 22, 0));

        EditTextBoldCursor search = new EditTextBoldCursor(context);
        search.lineYFix = true;
        search.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        search.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        search.setHintColor(getThemedColor(Theme.key_groupcreate_hintText));
        search.setHintText(getString(R.string.OEAiModelSearchOrEnter));
        search.setSingleLine(true);
        search.setBackground(null);
        search.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField),
                getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
                getThemedColor(Theme.key_text_RedRegular));
        search.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                typed = s == null ? "" : s.toString().trim();
                query = typed.toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });
        container.addView(search, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42,
                Gravity.LEFT | Gravity.TOP, 22, 48, 22, 0));

        adapter = new Adapter(context);
        listView = new RecyclerListView(context, resourcesProvider);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= filtered.size()) {
                return;
            }
            if (onPick != null) {
                onPick.run(filtered.get(position).getId());
            }
            dismiss();
        });
        container.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 100, 0, 0));

        progressView = new RadialProgressView(context, resourcesProvider);
        progressView.setSize(dp(30));
        container.addView(progressView, LayoutHelper.createFrame(48, 48, Gravity.CENTER));

        emptyView = new TextView(context);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
        emptyView.setVisibility(View.GONE);
        container.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 0));

        container.setMinimumHeight((int) (AndroidUtilities.displaySize.y * 0.68f));
        setCustomView(container);
        load();
    }

    private void load() {
        ModelsCatalog.load(loaded -> applyFilter());
        new Client.Builder().build().listModelsInfo((list, error) -> {
            progressView.setVisibility(View.GONE);
            loadError = TextUtils.isEmpty(error) ? null : error;
            models.clear();
            if (list != null) {
                models.addAll(list);
            }
            applyFilter();
        });
    }

    private void applyFilter() {
        filtered.clear();
        boolean exact = false;
        ArrayList<RemoteModel> matched = new ArrayList<>();
        for (RemoteModel model : models) {
            String id = model.getId();
            if (query.isEmpty()
                    || id.toLowerCase(Locale.ROOT).contains(query)
                    || model.getName().toLowerCase(Locale.ROOT).contains(query)) {
                matched.add(model);
            }
            exact = exact || id.equalsIgnoreCase(typed);
        }
        /*
         * Without a key the gateway only serves its free models, so a keyless
         * preset lists those. When the endpoint publishes no prices at all the
         * full list is kept, so local/self-hosted servers are not emptied.
         */
        boolean freeOnly = keylessService() && matched.stream().anyMatch(RemoteModel::isFree);
        for (RemoteModel model : matched) {
            if (!freeOnly || model.isFree()) {
                filtered.add(model);
            }
        }
        Collections.sort(filtered, (first, second) -> {
            if (first.isFree() != second.isFree()) {
                return first.isFree() ? -1 : 1;
            }
            return displayName(first).compareToIgnoreCase(displayName(second));
        });
        customModel = typed.isEmpty() || exact
                ? null : new RemoteModel(typed, typed, false, false, 0);
        if (customModel != null) {
            filtered.add(0, customModel);
        }
        emptyView.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        if (filtered.isEmpty()) {
            emptyView.setText(loadError == null ? getString(R.string.OEAiModelsEmpty) : loadError);
        }
        adapter.notifyDataSetChanged();
    }

    private static boolean keylessService() {
        app.exteraless.ai.data.Service service = app.exteraless.ai.AiConfig.getSelectedService();
        return service != null && service.isKeyless();
    }

    /** Endpoint name when it is meaningful, otherwise the models.dev name, then the id. */
    private static String displayName(RemoteModel model) {
        if (!TextUtils.equals(model.getName(), model.getId())) {
            return model.getName();
        }
        ModelInfo info = ModelsCatalog.get(model.getId());
        return info == null || TextUtils.isEmpty(info.getName()) ? model.getId() : info.getName();
    }

    private static String shortContext(long context) {
        if (context >= 1_000_000) {
            return String.format(Locale.US, "%.0fM", context / 1_000_000f);
        }
        if (context >= 1000) {
            return String.format(Locale.US, "%.0fK", context / 1000f);
        }
        return "";
    }

    private class ModelCell extends LinearLayout {

        private final TextView nameView;
        private final TextView idView;
        private final TextView badgeView;

        public ModelCell(Context context) {
            super(context);
            setOrientation(LinearLayout.HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(22), 0, dp(22), 0);

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);

            nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT));

            idView = new TextView(context);
            idView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            idView.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
            idView.setSingleLine(true);
            idView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            texts.addView(idView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

            addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f,
                    Gravity.CENTER_VERTICAL));

            badgeView = new TextView(context);
            badgeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            badgeView.setTypeface(AndroidUtilities.bold());
            badgeView.setGravity(Gravity.CENTER);
            badgeView.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
            badgeView.setPadding(dp(6), dp(2), dp(6), dp(2));
            badgeView.setBackground(Theme.createRoundRectDrawable(dp(4),
                    Theme.multAlpha(getThemedColor(Theme.key_dialogTextGray2), 0.12f)));
            addView(badgeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 10, 0, 0, 0));
        }

        public void bind(RemoteModel model) {
            if (model == customModel) {
                nameView.setText(model.getId());
                nameView.setTypeface(AndroidUtilities.bold());
                idView.setText(getString(R.string.OEAiModelUseCustom));
                idView.setVisibility(VISIBLE);
                badgeView.setVisibility(GONE);
                return;
            }
            ModelInfo info = ModelsCatalog.get(model.getId());
            String title = displayName(model);
            boolean reasoning = model.isReasoning() || (info != null && info.isReasoning());
            if (reasoning) {
                SpannableStringBuilder builder = new SpannableStringBuilder("\u2726 ");
                builder.setSpan(new ForegroundColorSpan(
                                getThemedColor(Theme.key_featuredStickers_addButton)),
                        0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.append(title);
                nameView.setText(builder);
            } else {
                nameView.setText(title);
            }
            nameView.setTypeface(TextUtils.equals(model.getId(), currentModel)
                    ? AndroidUtilities.bold() : null);
            idView.setText(model.getId());
            idView.setVisibility(TextUtils.equals(title, model.getId()) ? GONE : VISIBLE);
            if (model.isFree()) {
                badgeView.setText(getString(R.string.OEAiModelFree));
                bindBadgeColor(Theme.key_windowBackgroundWhiteGreenText);
                badgeView.setVisibility(VISIBLE);
            } else {
                long context = model.getContext() > 0
                        ? model.getContext() : (info == null ? 0 : info.getContext());
                String badge = context == 0 ? "" : shortContext(context);
                badgeView.setText(badge);
                bindBadgeColor(Theme.key_dialogTextGray2);
                badgeView.setVisibility(TextUtils.isEmpty(badge) ? GONE : VISIBLE);
            }
        }

        private void bindBadgeColor(int colorKey) {
            int color = getThemedColor(colorKey);
            badgeView.setTextColor(color);
            badgeView.setBackground(Theme.createRoundRectDrawable(dp(4), Theme.multAlpha(color, 0.14f)));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(
                            MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(56), MeasureSpec.EXACTLY));
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        public Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ModelCell cell = new ModelCell(context);
            cell.setBackground(Theme.getSelectorDrawable(false));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((ModelCell) holder.itemView).bind(filtered.get(position));
        }

        @Override
        public int getItemCount() {
            return filtered.size();
        }
    }
}
