package app.nimarkogram.messenger.preferences;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.nimarkogram.messenger.NimarkoConfig;

public class DialogsMenuOrderPreferencesActivity extends BaseFragment {

    private static final int MENU_RESET = 1;

    private RecyclerListView listView;
    private ListAdapter adapter;
    private ItemTouchHelper itemTouchHelper;

    private final ArrayList<Integer> workingOrder = new ArrayList<>();

    private static final int[] CATALOGUE = new int[] {
            NimarkoConfig.DIALOGS_MENU_THEME,
            NimarkoConfig.DIALOGS_MENU_READ_ALL,
            NimarkoConfig.DIALOGS_MENU_NEW_GROUP,
            NimarkoConfig.DIALOGS_MENU_SAVED,
    };

    private static Map<Integer, Integer> labelBySlot() {
        Map<Integer, Integer> m = new HashMap<>();
        m.put(NimarkoConfig.DIALOGS_MENU_THEME, R.string.NM_DM_Theme);
        m.put(NimarkoConfig.DIALOGS_MENU_READ_ALL, R.string.MarkAllAsRead);
        m.put(NimarkoConfig.DIALOGS_MENU_NEW_GROUP, R.string.NewGroup);
        m.put(NimarkoConfig.DIALOGS_MENU_SAVED, R.string.SavedMessages);
        return m;
    }

    private static Map<Integer, Integer> iconBySlot() {
        Map<Integer, Integer> m = new HashMap<>();
        m.put(NimarkoConfig.DIALOGS_MENU_THEME, R.drawable.menu_night_mode_24);
        m.put(NimarkoConfig.DIALOGS_MENU_READ_ALL, R.drawable.msg_markread);
        m.put(NimarkoConfig.DIALOGS_MENU_NEW_GROUP, R.drawable.outline_groups_24);
        m.put(NimarkoConfig.DIALOGS_MENU_SAVED, R.drawable.outline_saved_24);
        return m;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        buildWorkingOrder();
        return true;
    }

    private void buildWorkingOrder() {
        workingOrder.clear();
        Set<Integer> catalogue = new HashSet<>();
        for (int slot : CATALOGUE) catalogue.add(slot);

        List<Integer> persisted = NimarkoConfig.dialogsMenuOrder;
        Set<Integer> seen = new HashSet<>();
        if (persisted != null) {
            for (Integer slot : persisted) {
                if (slot != null && catalogue.contains(slot) && seen.add(slot)) {
                    workingOrder.add(slot);
                }
            }
        }
        for (int slot : CATALOGUE) {
            if (seen.add(slot)) workingOrder.add(slot);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setTitle(getString(R.string.NM_Menu_Reorder));
        actionBar.setAllowOverlayTitle(false);
        actionBar.setOccupyStatusBar(!AndroidUtilities.isTablet());

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem reset = menu.addItem(MENU_RESET, R.drawable.msg_reset);
        reset.setContentDescription(getString(R.string.NM_Menu_Reorder_Reset));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_RESET) {
                    NimarkoConfig.resetDialogsMenuOrder();
                    buildWorkingOrder();
                    if (adapter != null) adapter.notifyDataSetChanged();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        adapter = new ListAdapter(context);
        listView.setAdapter(adapter);
        if (listView.getItemAnimator() != null) {
            ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        }
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        itemTouchHelper = new ItemTouchHelper(new TouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(listView);

        return fragmentView;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        flush();
    }

    private void flush() {
        if (matchesCatalogue(workingOrder)) {
            NimarkoConfig.resetDialogsMenuOrder();
        } else {
            NimarkoConfig.setDialogsMenuOrder(new ArrayList<>(workingOrder));
        }
    }

    private static boolean matchesCatalogue(List<Integer> order) {
        if (order == null || order.size() != CATALOGUE.length) return false;
        for (int i = 0; i < CATALOGUE.length; i++) {
            Integer a = order.get(i);
            if (a == null || a != CATALOGUE[i]) return false;
        }
        return true;
    }

    private class TouchHelperCallback extends ItemTouchHelper.Callback {
        @Override
        public boolean isLongPressDragEnabled() { return true; }

        @Override
        public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView rv,
                              @NonNull RecyclerView.ViewHolder source,
                              @NonNull RecyclerView.ViewHolder target) {
            int from = source.getAdapterPosition();
            int to = target.getAdapterPosition();
            if (from < 0 || to < 0 || from >= workingOrder.size() || to >= workingOrder.size()) {
                return false;
            }
            Integer moved = workingOrder.remove(from);
            workingOrder.add(to, moved);
            adapter.notifyItemMoved(from, to);
            flush();
            return true;
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder vh, int actionState) {
            super.onSelectedChanged(vh, actionState);
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                try {
                    vh.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } catch (Throwable ignored) {}
            }
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {}
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context ctx;
        private final Map<Integer, Integer> labels = labelBySlot();
        private final Map<Integer, Integer> icons = iconBySlot();

        ListAdapter(Context context) { this.ctx = context; }

        @Override
        public int getItemCount() { return workingOrder.size(); }

        @Override
        public boolean isEnabled(@NonNull RecyclerView.ViewHolder holder) { return true; }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextCell cell = new TextCell(ctx);
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TextCell cell = (TextCell) holder.itemView;
            int slot = workingOrder.get(position);
            Integer labelRes = labels.get(slot);
            Integer iconRes = icons.get(slot);
            String text = labelRes != null ? LocaleController.getString(labelRes) : ("#" + slot);
            int icon = iconRes != null ? iconRes : R.drawable.msg_reorder;
            cell.setTextAndIcon(text, icon, position < workingOrder.size() - 1);
        }
    }
}
