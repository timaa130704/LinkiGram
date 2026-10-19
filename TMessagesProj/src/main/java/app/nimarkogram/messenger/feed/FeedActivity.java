package app.nimarkogram.messenger.feed;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.DateFormat;
import java.util.*;
import org.telegram.messenger.*;
import org.telegram.tgnet.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.Components.*;

/** Independent timeline. Reading the feed never sends a read acknowledgement. */
public class FeedActivity extends BaseFragment {
    private final ArrayList<MessageObject> messages = new ArrayList<>();
    private final ArrayList<MessageObject> loaded = new ArrayList<>();
    private final HashMap<Long, Integer> oldestDates = new HashMap<>();
    private final ArrayDeque<Long> requests = new ArrayDeque<>();
    private int activeRequests;
    private final HashMap<Long, Integer> cursors = new HashMap<>();
    private final HashSet<Long> exhausted = new HashSet<>();
    private final HashSet<String> identities = new HashSet<>();
    private Set<String> selected = new HashSet<>();
    private RecyclerView recycler;
    private Adapter adapter;
    private TextView status;
    private int generation, pending, failures;
    private boolean destroyed;

    @Override public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(R.string.LF_Title));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.createMenu().addItem(1, R.drawable.msg_customize);
        actionBar.createMenu().addItem(2, R.drawable.msg_retry);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == 1) openSources();
                else if (id == 2) reload();
            }
        });
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        status = new TextView(context);
        status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        status.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        status.setGravity(Gravity.CENTER);
        status.setOnClickListener(v -> { if (selected.isEmpty()) openSources(); else loadOlder(); });
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        recycler = new RecyclerView(context);
        recycler.setLayoutManager(new LinearLayoutManager(context));
        recycler.setAdapter(adapter = new Adapter());
        root.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1));
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView r, int dx, int dy) {
                if (dy > 0 && !r.canScrollVertically(1)) loadOlder();
            }
        });
        fragmentView = root;
        reload();
        return root;
    }
    @Override public void onResume() {
        super.onResume();
        if (adapter == null) return;
        if (!selected.equals(FeedPreferences.sources(currentAccount))) reload();
        else adapter.notifyDataSetChanged();
    }
    @Override public void onFragmentDestroy() {
        destroyed = true; generation++;
        getConnectionsManager().cancelRequestsForGuid(classGuid);
        super.onFragmentDestroy();
    }
    private void openSources() {
        FeedSourcesActivity picker = new FeedSourcesActivity();
        picker.setCurrentAccount(currentAccount);
        presentFragment(picker);
    }
    private void reload() {
        generation++;
        getConnectionsManager().cancelRequestsForGuid(classGuid);
        selected = FeedPreferences.sources(currentAccount);
        messages.clear(); loaded.clear(); oldestDates.clear(); requests.clear(); activeRequests = 0; cursors.clear(); exhausted.clear(); identities.clear(); pending = 0; failures = 0;
        adapter.notifyDataSetChanged();
        loadOlder();
    }
    private void updateStatus() {
        int key = selected.isEmpty() ? R.string.LF_Empty : pending > 0 ? R.string.Loading
                : failures > 0 ? R.string.LF_Retry : messages.isEmpty() ? R.string.LF_NoMessages
                : exhausted.size() == selected.size() ? R.string.LF_End : R.string.LF_LoadMore;
        status.setText(LocaleController.getString(key));
    }
    private void loadOlder() {
        if (destroyed || pending != 0 || selected.isEmpty()) { updateStatus(); return; }
        int token = generation;
        failures = 0;
        ArrayList<Long> batch = new ArrayList<>();
        for (String raw : selected) {
            try {
                long id = Long.parseLong(raw);
                if (!exhausted.contains(id)) batch.add(id);
            } catch (NumberFormatException ignored) {}
        }
        pending = batch.size(); updateStatus();
        requests.addAll(batch);
        pump(token);
    }
    private void pump(int token) {
        while (activeRequests < 3 && !requests.isEmpty()) {
            long id = requests.removeFirst();
            activeRequests++;
            TLRPC.TL_messages_getHistory request = new TLRPC.TL_messages_getHistory();
            request.peer = getMessagesController().getInputPeer(id);
            request.offset_id = cursors.getOrDefault(id, 0);
            request.limit = 20;
            int requestId = getConnectionsManager().sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (destroyed || token != generation) return;
                pending--; activeRequests--;
                if (response instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages result = (TLRPC.messages_Messages) response;
                    getMessagesController().putUsers(result.users, false);
                    getMessagesController().putChats(result.chats, false);
                    int cursor = cursors.getOrDefault(id, 0);
                    for (TLRPC.Message message : result.messages) {
                        if (message.id > 0 && (cursor == 0 || message.id < cursor)) cursor = message.id;
                        if (message.date > 0) oldestDates.put(id, Math.min(oldestDates.getOrDefault(id, Integer.MAX_VALUE), message.date));
                        if (!(message instanceof TLRPC.TL_message)) continue;
                        String identity = id + ":" + message.id;
                        if (identities.add(identity)) loaded.add(new MessageObject(currentAccount, message, true, true));
                    }
                    if (result.messages.isEmpty() || cursor == cursors.getOrDefault(id, 0)) exhausted.add(id);
                    cursors.put(id, cursor);
                } else failures++;
                if (pending == 0) publish();
                pump(token);
                updateStatus();
            }));
            getConnectionsManager().bindRequestToGuid(requestId, classGuid);
        }
    }
    private void publish() {
        // Only expose a chronological interval for which all sources were fetched.
        int cutoff = 0;
        for (String raw : selected) {
            long id = Long.parseLong(raw);
            if (!exhausted.contains(id)) cutoff = Math.max(cutoff, oldestDates.getOrDefault(id, 0));
        }
        messages.clear();
        for (MessageObject message : loaded) if (message.messageOwner.date >= cutoff) messages.add(message);
        messages.sort((a, b) -> {
            int d = Integer.compare(b.messageOwner.date, a.messageOwner.date);
            if (d == 0) d = Long.compare(b.getDialogId(), a.getDialogId());
            return d == 0 ? Integer.compare(b.getId(), a.getId()) : d;
        });
        adapter.notifyDataSetChanged();
    }
    private void open(MessageObject message) {
        long id = message.getDialogId();
        Bundle args = new Bundle();
        args.putLong(id < 0 ? "chat_id" : "user_id", Math.abs(id));
        args.putInt("message_id", message.getId());
        ChatActivity chat = new ChatActivity(args);
        chat.setCurrentAccount(currentAccount);
        presentFragment(chat);
    }
    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return messages.size(); }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            return new RecyclerView.ViewHolder(new Card(parent.getContext())) {};
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((Card) holder.itemView).bind(messages.get(position));
        }
    }
    private class Card extends LinearLayout {
        final TextView source, body, details;
        final BackupImageView image;
        Card(Context context) {
            super(context); setOrientation(VERTICAL);
            setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            source = new TextView(context); source.setTypeface(AndroidUtilities.bold());
            body = new TextView(context); details = new TextView(context);
            image = new BackupImageView(context);
            addView(source, new LinearLayout.LayoutParams(-1, -2));
            addView(image, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(220)));
            addView(body, new LinearLayout.LayoutParams(-1, -2));
            addView(details, new LinearLayout.LayoutParams(-1, -2));
        }
        void bind(MessageObject message) {
            boolean compact = FeedPreferences.compact("feed");
            int padding = AndroidUtilities.dp(compact ? 10 : 18);
            setPadding(padding, padding, padding, padding);
            GradientDrawable background = new GradientDrawable();
            background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            background.setCornerRadius(AndroidUtilities.dp(compact ? 8 : 16));
            setBackground(background);
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) getLayoutParams();
            lp.setMargins(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
            source.setTextColor(FeedPreferences.accent("feed", Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)));
            source.setTextSize(14);
            source.setText(FeedSourcesActivity.name(currentAccount, message.getDialogId()));
            source.setOnClickListener(v -> open(message));
            body.setTextSize(compact ? 14 : 16);
            body.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            CharSequence text = message.caption != null ? message.caption : message.messageText;
            boolean spoilers = false;
            if (message.messageOwner.entities != null) for (TLRPC.MessageEntity entity : message.messageOwner.entities) {
                if (entity instanceof TLRPC.TL_messageEntitySpoiler) { spoilers = true; break; }
            }
            body.setText(spoilers ? LocaleController.getString(R.string.LF_Spoiler) : text);
            details.setTextSize(12);
            details.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            details.setText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(message.messageOwner.date * 1000L)));
            details.setVisibility(FeedPreferences.labels("feed") ? VISIBLE : GONE);
            // Respect spoiler and ephemeral-media protection: no preview in these cases.
            boolean preview = !message.hasMediaSpoilers() && !message.shouldEncryptPhotoOrVideo();
            TLRPC.PhotoSize thumb = preview ? FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 320) : null;
            image.setVisibility(thumb == null ? GONE : VISIBLE);
            image.setImage(thumb == null ? null : ImageLocation.getForObject(thumb, message.photoThumbsObject), "320_220", (android.graphics.drawable.Drawable) null, message);
            image.setOnClickListener(v -> {
                if (message.isPhoto() || message.isVideo() || message.isGif()) {
                    PhotoViewer viewer = PhotoViewer.getInstance();
                    viewer.setParentActivity(FeedActivity.this);
                    viewer.openPhoto(message, message.getDialogId(), 0, 0, new PhotoViewer.EmptyPhotoViewerProvider(), true);
                } else open(message);
            });
            setOnClickListener(v -> open(message));
        }
    }
}
