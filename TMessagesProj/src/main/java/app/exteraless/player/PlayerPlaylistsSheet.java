package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/**
 * Bottom sheet listing the local {@link PlayerPlaylists}: the first level shows
 * playlists, tapping one opens its tracks inside the same sheet. Tracks are
 * resolved back to a {@link MessageObject} and handed to the media controller.
 */
public class PlayerPlaylistsSheet extends BottomSheet {

    private final PlayerColors colors;
    private final int account;
    private final FrameLayout root;
    private final ImageView backButton;
    private final PlayerIcon backIcon = PlayerIcon.stroke(PlayerIcon.CHEVRON_DOWN, 24);
    private final TextView titleView;
    private final TextView subtitle;
    private final RecyclerListView list;
    private final LinearLayoutManager layoutManager;
    private final Adapter adapter = new Adapter();
    private ArrayList<PlayerPlaylists.Playlist> playlists = new ArrayList<>();
    private PlayerPlaylists.Playlist current;
    private boolean touchInList;

    public PlayerPlaylistsSheet(Context context, PlayerColors colors, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        this.colors = colors;
        occupyNavigationBar = true;
        drawNavigationBar = false;
        setApplyTopPadding(false);
        setApplyBottomPadding(false);
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        account = playing != null ? playing.currentAccount : currentAccount;
        currentAccount = account;

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colors.surfaceLow);
        bg.setCornerRadii(new float[]{dp(28), dp(28), dp(28), dp(28), 0, 0, 0, 0});
        root = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int h = (int) (MeasureSpec.getSize(heightMeasureSpec) * 0.78f);
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                int action = ev.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    touchInList = ev.getY() >= list.getTop();
                }
                boolean result = super.dispatchTouchEvent(ev);
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && touchInList) {
                    touchInList = false;
                    container.requestDisallowInterceptTouchEvent(false);
                }
                return result;
            }
        };
        root.setBackground(bg);
        containerView = root;

        View handle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setCornerRadius(dp(2));
        handleBg.setColor(ColorUtils.setAlphaComponent(colors.onSurfaceVariant, 0x73));
        handle.setBackground(handleBg);
        root.addView(handle, LayoutHelper.createFrame(32, 4, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 18, 0, 0));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), 0, dp(16), 0);

        backButton = new ImageView(context);
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backButton.setImageDrawable(backIcon);
        backButton.setRotation(LocaleController.isRTL ? -90 : 90);
        backIcon.setColor(colors.onSurface);
        backButton.setContentDescription(getString(R.string.Back));
        backButton.setBackground(Theme.createSelectorDrawable(colors.ripple(), Theme.RIPPLE_MASK_CIRCLE_20DP));
        backButton.setOnClickListener(v -> openLevel1());
        backButton.setVisibility(View.INVISIBLE);
        header.addView(backButton, LayoutHelper.createLinear(40, 48, Gravity.CENTER_VERTICAL));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(colors.onSurface);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        subtitle = new TextView(context);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setTextColor(colors.onSurfaceVariant);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        header.addView(titles, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 8, 0, 0, 0));

        ImageView addButton = new ImageView(context);
        addButton.setScaleType(ImageView.ScaleType.CENTER);
        addButton.setImageResource(R.drawable.msg_add);
        addButton.setColorFilter(colors.onSurface);
        addButton.setContentDescription(getString(R.string.OEPlayerPlaylistCreate));
        addButton.setBackground(Theme.createSelectorDrawable(colors.ripple(), Theme.RIPPLE_MASK_CIRCLE_20DP));
        addButton.setOnClickListener(v -> createPlaylistDialog(null));
        header.addView(addButton, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL));
        root.addView(header, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 72, Gravity.TOP, 0, 34, 0, 0));

        list = new RecyclerListView(context) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                setPadding(dp(12), 0, dp(12), dp(24) + getBottomInset());
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        layoutManager = new LinearLayoutManager(context);
        list.setLayoutManager(layoutManager);
        list.setAdapter(adapter);
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        list.setSelectorDrawableColor(0);
        list.setOnItemClickListener((view, position) -> onItemClick(position));
        list.setOnItemLongClickListener((view, position) -> {
            showItemMenu(view, position);
            return true;
        });
        root.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 34 + 72 + 8, 0, 0));

        reload();
        AndroidUtilities.setLightNavigationBar(this, ColorUtils.calculateLuminance(colors.surfaceLow) > 0.5);
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return !touchInList || !list.canScrollVertically(-1);
    }

    @Override
    public void onBackPressed() {
        if (current != null) {
            openLevel1();
            return;
        }
        super.onBackPressed();
    }

    private void reload() {
        playlists = new ArrayList<>(PlayerPlaylists.list());
        if (current != null) {
            current = findPlaylist(current.id);
        }
        updateHeader();
        adapter.notifyDataSetChanged();
    }

    private PlayerPlaylists.Playlist findPlaylist(long id) {
        for (int i = 0; i < playlists.size(); i++) {
            if (playlists.get(i).id == id) {
                return playlists.get(i);
            }
        }
        return null;
    }

    private void updateHeader() {
        if (current == null) {
            titleView.setText(getString(R.string.OEPlayerPlaylists));
            subtitle.setText(LocaleController.formatPluralString("OEPlayerPlaylistsCount", playlists.size()));
            backButton.setVisibility(View.INVISIBLE);
        } else {
            titleView.setText(current.name);
            subtitle.setText(LocaleController.formatPluralString("OEPlayerTracks", current.entries.size()));
            backButton.setVisibility(View.VISIBLE);
        }
    }

    private void openLevel1() {
        current = null;
        layoutManager.scrollToPosition(0);
        updateHeader();
        adapter.notifyDataSetChanged();
    }

    private void openPlaylist(PlayerPlaylists.Playlist playlist) {
        current = playlist;
        layoutManager.scrollToPosition(0);
        updateHeader();
        adapter.notifyDataSetChanged();
    }

    private void onItemClick(int position) {
        if (current == null) {
            if (position >= 0 && position < playlists.size()) {
                openPlaylist(playlists.get(position));
            }
        } else {
            if (position >= 0 && position < current.entries.size()) {
                playEntry(current.entries.get(position));
            }
        }
    }

    private void showItemMenu(View anchor, int position) {
        Theme.ResourcesProvider rp = colors.provider(resourcesProvider);
        ItemOptions o = ItemOptions.makeOptions(container, rp, anchor, true);
        if (current == null) {
            if (position < 0 || position >= playlists.size()) {
                return;
            }
            PlayerPlaylists.Playlist playlist = playlists.get(position);
            o.add(R.drawable.msg_edit, getString(R.string.OEPlayerPlaylistRename), () -> renamePlaylistDialog(playlist));
            o.add(R.drawable.msg_delete, getString(R.string.OEPlayerPlaylistDelete), true, () -> {
                PlayerPlaylists.delete(playlist.id);
                reload();
            });
        } else {
            if (position < 0 || position >= current.entries.size()) {
                return;
            }
            final long playlistId = current.id;
            final int index = position;
            o.add(R.drawable.msg_delete, getString(R.string.OEPlayerPlaylistRemove), true, () -> {
                PlayerPlaylists.removeEntry(playlistId, index);
                reload();
            });
        }
        o.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        o.show();
    }

    private void createPlaylistDialog(MessageObject messageObject) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        builder.setTitle(getString(R.string.OEPlayerPlaylistCreate));
        final EditText input = new EditText(getContext());
        input.setHint(getString(R.string.OEPlayerPlaylistName));
        input.setSingleLine(true);
        input.setTextColor(colors.onSurface);
        input.setHintTextColor(ColorUtils.setAlphaComponent(colors.onSurfaceVariant, 0xc0));
        builder.setView(input);
        builder.setPositiveButton(getString(R.string.Create), (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                name = getString(R.string.OEPlayerPlaylistNew);
            }
            PlayerPlaylists.Playlist playlist = PlayerPlaylists.create(name);
            if (messageObject != null) {
                PlayerPlaylists.addTrack(playlist.id, messageObject);
            }
            reload();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void renamePlaylistDialog(PlayerPlaylists.Playlist playlist) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        builder.setTitle(getString(R.string.OEPlayerPlaylistRename));
        final EditText input = new EditText(getContext());
        input.setSingleLine(true);
        input.setText(playlist.name);
        input.setTextColor(colors.onSurface);
        input.setSelection(input.getText().length());
        builder.setView(input);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            PlayerPlaylists.rename(playlist.id, input.getText().toString());
            reload();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void showError() {
        BulletinFactory.of(root, colors.provider(resourcesProvider))
                .createSimpleBulletin(R.raw.ic_delete, getString(R.string.OEPlayerPlaylistOpenFailed)).show();
    }

    private void play(MessageObject messageObject) {
        if (messageObject == null) {
            showError();
            return;
        }
        MediaController.getInstance().playMessage(messageObject);
        dismiss();
    }

    private void playEntry(PlayerPlaylists.Entry entry) {
        MessageObject inMemory = resolveInMemory(entry);
        if (inMemory != null) {
            play(inMemory);
            return;
        }
        final PlayerPlaylists.Entry target = entry;
        Utilities.globalQueue.postRunnable(() -> {
            TLRPC.Message message = MessagesStorage.getInstance(target.account).getMessage(target.dialogId, target.messageId);
            if (message == null) {
                AndroidUtilities.runOnUIThread(this::showError);
                return;
            }
            final MessageObject stored = new MessageObject(target.account, message, false, true);
            PlayerActions.refetch(stored, fresh -> AndroidUtilities.runOnUIThread(() -> play(fresh != null ? fresh : stored)));
        });
    }

    private MessageObject resolveInMemory(PlayerPlaylists.Entry entry) {
        MessagesController controller = MessagesController.getInstance(entry.account);
        ArrayList<MessageObject> dialogMessages = controller.dialogMessage.get(entry.dialogId);
        for (int i = 0; dialogMessages != null && i < dialogMessages.size(); i++) {
            MessageObject mo = dialogMessages.get(i);
            if (mo != null && mo.getId() == entry.messageId) {
                return mo;
            }
        }
        MessageObject byId = controller.dialogMessagesByIds.get(entry.messageId);
        if (byId != null && byId.getDialogId() == entry.dialogId) {
            return byId;
        }
        return null;
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Row row = new Row(parent.getContext());
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
            return new RecyclerListView.Holder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((Row) holder.itemView).bind(position);
        }

        @Override
        public int getItemCount() {
            return current == null ? playlists.size() : current.entries.size();
        }
    }

    private class Row extends FrameLayout {

        private final TextView tile;
        private final TextView title;
        private final TextView subtitle;
        private final ImageView more;
        private final PlayerIcon noteIcon = PlayerIcon.stroke(PlayerIcon.NOTE, 22);
        private final PlayerIcon moreIcon = PlayerIcon.fill(PlayerIcon.MORE, 20);
        private final GradientDrawable bg = new GradientDrawable();
        private final GradientDrawable tileBg = new GradientDrawable();
        private int position;

        Row(Context context) {
            super(context);
            bg.setCornerRadius(dp(20));
            setBackground(bg);
            setPadding(dp(10), 0, dp(6), 0);
            tile = new TextView(context);
            tile.setGravity(Gravity.CENTER);
            tileBg.setCornerRadius(dp(14));
            tileBg.setColor(colors.primaryContainer);
            tile.setBackground(tileBg);
            noteIcon.setColor(colors.onPrimaryContainer);
            tile.setCompoundDrawablesWithIntrinsicBounds(noteIcon, null, null, null);
            addView(tile, LayoutHelper.createFrame(52, 52, Gravity.LEFT | Gravity.CENTER_VERTICAL));
            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTypeface(AndroidUtilities.bold());
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setTextColor(colors.onSurface);
            texts.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            subtitle = new TextView(context);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setTextColor(colors.onSurfaceVariant);
            texts.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 66, 0, 48, 0));
            more = new ImageView(context);
            more.setScaleType(ImageView.ScaleType.CENTER);
            more.setImageDrawable(moreIcon);
            moreIcon.setColor(colors.onSurfaceVariant);
            more.setContentDescription(getString(R.string.AccDescrMoreOptions));
            more.setBackground(Theme.createSelectorDrawable(colors.ripple(), Theme.RIPPLE_MASK_CIRCLE_20DP));
            more.setOnClickListener(v -> showItemMenu(v, position));
            addView(more, LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        }

        void bind(int position) {
            this.position = position;
            if (current == null) {
                PlayerPlaylists.Playlist playlist = position >= 0 && position < playlists.size() ? playlists.get(position) : null;
                if (playlist == null) {
                    return;
                }
                title.setText(playlist.name);
                subtitle.setText(LocaleController.formatPluralString("OEPlayerTracks", playlist.entries.size()));
            } else {
                PlayerPlaylists.Entry entry = position >= 0 && position < current.entries.size() ? current.entries.get(position) : null;
                if (entry == null) {
                    return;
                }
                title.setText(TextUtils.isEmpty(entry.title) ? getString(R.string.OEPlayerMusic) : entry.title);
                subtitle.setText(entry.author);
            }
        }
    }
}