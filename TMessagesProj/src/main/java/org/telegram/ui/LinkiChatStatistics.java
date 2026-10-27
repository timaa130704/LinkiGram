package org.telegram.ui;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

/** Counts only the history the current account can actually retrieve. */
public final class LinkiChatStatistics {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_MESSAGES = 3000;

    private LinkiChatStatistics() {}

    public static void show(ChatActivity fragment, long dialogId) {
        Context context = fragment.getParentActivity();
        if (context == null) return;
        String[] periods = {text(R.string.LinkiChatStatsWeek), text(R.string.LinkiChatStatsMonth), text(R.string.LinkiChatStatsThreeMonths)};
        int[] days = {7, 30, 90};
        fragment.showDialog(new AlertDialog.Builder(context, fragment.getResourceProvider())
                .setTitle(text(R.string.LinkiChatStatsTitle))
                .setItems(periods, (dialog, which) -> new Loader(fragment, dialogId, days[which], periods[which]).start())
                .setNegativeButton(text(R.string.Cancel), null)
                .create());
    }

    private static String text(int resource) {
        return LocaleController.getString(resource);
    }

    private static final class Loader {
        private final ChatActivity fragment;
        private final long dialogId;
        private final String period;
        private final int since;
        private final MessagesController controller;
        private final ConnectionsManager connections;
        private final HashSet<Integer> seen = new HashSet<>();
        private final HashMap<Long, Integer> authors = new HashMap<>();
        private AlertDialog progress;
        private int requestId;
        private int count;
        private int media;
        private boolean stopped;

        Loader(ChatActivity fragment, long dialogId, int days, String period) {
            this.fragment = fragment;
            this.dialogId = dialogId;
            this.period = period;
            this.since = (int) (System.currentTimeMillis() / 1000L - days * 86400L);
            this.controller = MessagesController.getInstance(fragment.getCurrentAccount());
            this.connections = ConnectionsManager.getInstance(fragment.getCurrentAccount());
        }

        void start() {
            Context context = fragment.getParentActivity();
            if (context == null) return;
            progress = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER, fragment.getResourceProvider());
            progress.setOnCancelListener(dialog -> {
                stopped = true;
                if (requestId != 0) connections.cancelRequest(requestId, true);
            });
            if (fragment.showDialog(progress) == null) return;
            load(0);
        }

        void load(int offset) {
            if (stopped) return;
            TLRPC.TL_messages_getHistory request = new TLRPC.TL_messages_getHistory();
            request.peer = controller.getInputPeer(dialogId);
            request.offset_id = offset;
            request.limit = PAGE_SIZE;
            requestId = connections.sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (stopped || fragment.getParentActivity() == null) return;
                requestId = 0;
                if (!(response instanceof TLRPC.messages_Messages)) {
                    finish(true, true);
                    return;
                }
                TLRPC.messages_Messages result = (TLRPC.messages_Messages) response;
                controller.putUsers(result.users, false);
                controller.putChats(result.chats, false);
                int next = 0;
                boolean reachedStart = result.messages.isEmpty();
                for (TLRPC.Message message : result.messages) {
                    if (message.id <= 0) continue;
                    if (next == 0 || message.id < next) next = message.id;
                    if (message.date < since) { reachedStart = true; continue; }
                    if (!seen.add(message.id) || message instanceof TLRPC.TL_messageService || message instanceof TLRPC.TL_messageEmpty) continue;
                    count++;
                    if (message.media != null && !(message.media instanceof TLRPC.TL_messageMediaEmpty)
                            && !(message.media instanceof TLRPC.TL_messageMediaWebPage)) media++;
                    long author = MessageObject.getFromChatId(message);
                    if (author != 0) authors.put(author, authors.getOrDefault(author, 0) + 1);
                }
                if (reachedStart || result.messages.size() < PAGE_SIZE) finish(false, false);
                else if (seen.size() >= MAX_MESSAGES || next == 0 || next == offset) finish(true, false);
                else load(next);
            }));
            connections.bindRequestToGuid(requestId, fragment.getClassGuid());
        }

        void finish(boolean incomplete, boolean error) {
            if (stopped) return;
            stopped = true;
            progress.dismiss();
            if (fragment.getParentActivity() == null) return;
            StringBuilder body = new StringBuilder();
            body.append(period).append('\n');
            body.append(String.format(Locale.getDefault(), text(R.string.LinkiChatStatsMessages), count)).append('\n');
            body.append(String.format(Locale.getDefault(), text(R.string.LinkiChatStatsMedia), media)).append('\n');
            body.append(String.format(Locale.getDefault(), text(R.string.LinkiChatStatsAuthors), authors.size()));
            ArrayList<Map.Entry<Long, Integer>> top = new ArrayList<>(authors.entrySet());
            Collections.sort(top, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
            if (!top.isEmpty()) body.append("\n\n").append(text(R.string.LinkiChatStatsTop));
            for (int i = 0; i < Math.min(10, top.size()); i++) {
                Map.Entry<Long, Integer> entry = top.get(i);
                long id = entry.getKey();
                TLRPC.User user = id > 0 ? controller.getUser(id) : null;
                TLRPC.Chat chat = id < 0 ? controller.getChat(-id) : null;
                String name = user != null ? UserObject.getUserName(user) : chat != null ? chat.title : text(R.string.LinkiChatStatsUnknown);
                body.append('\n').append(i + 1).append(". ").append(name).append(" — ").append(entry.getValue());
            }
            if (incomplete) body.append("\n\n").append(text(error ? R.string.LinkiChatStatsError : R.string.LinkiChatStatsLimit));
            body.append("\n\n").append(text(R.string.LinkiChatStatsScope));
            fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                    .setTitle(text(R.string.LinkiChatStatsTitle))
                    .setMessage(body.toString())
                    .setPositiveButton(text(R.string.OK), null)
                    .create());
        }
    }
}
