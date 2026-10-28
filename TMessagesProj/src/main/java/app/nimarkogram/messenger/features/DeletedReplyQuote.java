package app.nimarkogram.messenger.features;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;

public final class DeletedReplyQuote {
    public static void rewrite(int account, SendMessagesHelper.SendMessageParams params) {
        if (!ChatExtras.enabled("deletedQuote") || params == null || params.retryMessageObject != null) return;
        MessageObject reply = params.replyToMsg;
        if (reply == null || reply.messageOwner == null || reply.getId() <= 0) return;
        long uid = UserConfig.getInstance(account).getClientUserId();
        if (!reply.isAyuDeleted() && !com.radolyn.ayugram.messages.AyuMessagesController.getInstance()
                .isAyuDeletedMessageId(uid, reply.getDialogId(), reply.getId())) return;
        String quote = params.replyQuote != null ? params.replyQuote.getText() : reply.messageOwner.message;
        if ((quote == null || quote.trim().isEmpty()) && reply.messageText != null) quote = reply.messageText.toString();
        if (quote == null || quote.trim().isEmpty()) return;
        long sender = reply.getFromChatId();
        String author = sender != 0 ? DialogObject.getName(account, sender) : reply.messageOwner.post_author;
        if (author == null || author.trim().isEmpty()) author = LocaleController.getString(R.string.HiddenName);
        author = author.replace('\n', ' ').replace('\r', ' ').trim();
        if (author.length() > 100) author = author.substring(0, 100);
        // Captions have a much smaller server limit. Keep the body intact and
        // truncate only the quote, with an explicit ellipsis.
        String body = params.message != null ? params.message : params.caption;
        if (body == null) body = "";
        int maximum = params.message != null ? 4096 : 1024;
        int available = maximum - body.length() - author.length() - 2;
        if (available <= 1) return;
        if (quote.length() > available) {
            int end = available - 1;
            if (end > 0 && Character.isHighSurrogate(quote.charAt(end - 1))) end--;
            quote = quote.substring(0, end) + "…";
        }
        String quotedText = author + "\n" + quote;
        String prefix = quotedText + "\n";
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        if (params.entities != null) for (TLRPC.MessageEntity old : params.entities) {
            org.telegram.tgnet.SerializedData data = new org.telegram.tgnet.SerializedData();
            old.serializeToStream(data);
            org.telegram.tgnet.SerializedData reader = new org.telegram.tgnet.SerializedData(data.toByteArray());
            TLRPC.MessageEntity copy = TLRPC.MessageEntity.TLdeserialize(reader, reader.readInt32(false), false);
            reader.cleanup(); data.cleanup();
            if (copy != null) { copy.offset += prefix.length(); entities.add(copy); }
        }
        TLRPC.TL_messageEntityBlockquote entity = new TLRPC.TL_messageEntityBlockquote();
        entity.offset = 0; entity.length = quotedText.length(); entities.add(entity);
        TLRPC.TL_messageEntityBold authorEntity = new TLRPC.TL_messageEntityBold();
        authorEntity.offset = 0; authorEntity.length = author.length(); entities.add(authorEntity);
        params.entities = entities;
        if (params.message != null) params.message = prefix + body; else params.caption = prefix + body;
        params.replyToMsg = params.replyToTopMsg;
        params.replyQuote = null;
    }
    private DeletedReplyQuote() { }
}
