package app.nimarkogram.messenger.preferences;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

/** Adapts the exteraless assistant settings to LinkiGram's native settings cells. */
public abstract class AiSettingsBaseActivity extends BaseFragment {
    protected static final int TYPE_SETTINGS = 2, TYPE_CHECK = 3, TYPE_HEADER = 4, TYPE_INFO_PRIVACY = 7;
    protected int rowCount;
    protected BaseListAdapter listAdapter;
    protected Theme.ResourcesProvider resourcesProvider;
    protected RecyclerListView listView;

    protected abstract String getActionBarTitle();
    protected abstract String getKey();
    public abstract int getSearchGuid();
    public abstract int getSearchIcon();
    public abstract String getSearchPrefix();
    protected abstract BaseListAdapter createAdapter(Context context);
    protected abstract void onItemClick(View view, int position, float x, float y);
    protected void updateRows() { rowCount = 0; }
    protected int addRow(String... keys) { return rowCount++; }

    @Override public boolean onFragmentCreate() {
        updateRows();
        return super.onFragmentCreate();
    }
    @Override public View createView(Context context) {
        resourcesProvider = getResourceProvider();
        actionBar.setTitle(getActionBarTitle());
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        fragmentView = frame;
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listAdapter = createAdapter(context);
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((RecyclerListView.OnItemClickListenerExtended) this::onItemClick);
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }
    protected abstract class BaseListAdapter extends RecyclerListView.SelectionAdapter {
        protected final Context mContext;
        protected BaseListAdapter(Context context) { mContext = context; }
        @Override public int getItemCount() { return rowCount; }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_SETTINGS || holder.getItemViewType() == TYPE_CHECK;
        }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            View view;
            if (type == TYPE_HEADER) view = new HeaderCell(mContext, resourcesProvider);
            else if (type == TYPE_CHECK) view = new TextCheckCell(mContext);
            else if (type == TYPE_INFO_PRIVACY) view = new TextInfoPrivacyCell(mContext);
            else view = new TextSettingsCell(mContext);
            if (type != TYPE_INFO_PRIVACY) view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            onBindViewHolder(holder, position, false);
        }
        public abstract void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial);
    }
}
