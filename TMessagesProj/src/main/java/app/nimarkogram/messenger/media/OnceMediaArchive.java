package app.nimarkogram.messenger.media;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import app.nimarkogram.messenger.NimarkoConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.secretmedia.EncryptedFileInputStream;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.SerializedData;
import android.util.AtomicFile;

/** Copies only media the user opens; never changes Telegram read/deletion requests. */
public final class OnceMediaArchive {
    private static final ConcurrentHashMap<String, Entry> pending = new ConcurrentHashMap<>();
    private static final java.util.Set<String> copying = ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Integer> observed = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<Long, AtomicLong> generations = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, java.util.Set<String>> snapshots = new ConcurrentHashMap<>();
    private static final java.util.Set<String> viewed = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, byte[]> snapshotBytes = new ConcurrentHashMap<>();
    private static final long WAIT_MS = 120_000L;

    private static AtomicLong generation(long uid) {
        return generations.computeIfAbsent(uid, key -> new AtomicLong());
    }

    public static File directory(int account) {
        long uid = UserConfig.getInstance(account).getClientUserId();
        return new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "once_media/" + uid);
    }

    private static final class Entry {
        final int account;
        final long uid, generation;
        final File source, target;
        final String key, base;
        final long dialogId;
        final int messageId;
        final byte[] snapshot;
        Entry(MessageObject message) {
            account = message.currentAccount;
            uid = UserConfig.getInstance(account).getClientUserId();
            generation = generation(uid).get();
            dialogId = message.getDialogId();
            messageId = message.getId();
            base = dialogId + "_" + messageId;
            SerializedData serialized = new SerializedData(message.messageOwner.getObjectSize());
            message.messageOwner.serializeToStream(serialized);
            snapshot = serialized.toByteArray();
            serialized.cleanup();
            File attachment = message.messageOwner.attachPath == null || message.messageOwner.attachPath.isEmpty()
                    ? null : new File(message.messageOwner.attachPath);
            source = attachment != null && attachment.isFile() ? attachment
                    : FileLoader.getInstance(account).getPathToMessage(message.messageOwner);
            TLRPC.Document document = message.getDocument();
            String mime = document == null ? "" : document.mime_type;
            String ext = message.isVoiceOnce() ? ".ogg" : message.isRoundOnce() || message.isVideo()
                    || "video/mp4".equals(mime) ? ".mp4" : "image/gif".equals(mime) ? ".gif" : ".jpg";
            target = new File(directory(account), base + ext);
            key = uid + ":" + target.getName();
        }
        boolean allowed() {
            return uid > 0 && NimarkoConfig.keepOpenedOnceMedia
                    && UserConfig.getInstance(account).getClientUserId() == uid
                    && generation(uid).get() == generation;
        }
    }

    public static void capture(MessageObject message) {
        if (!NimarkoConfig.keepOpenedOnceMedia || message == null || message.messageOwner == null) return;
        TLRPC.MessageMedia media = MessageObject.getMedia(message);
        if (!message.isVoiceOnce() && !message.isRoundOnce() && message.messageOwner.ttl != Integer.MAX_VALUE
                && (media == null || media.ttl_seconds != Integer.MAX_VALUE)) return;
        try {
            Entry entry = new Entry(message);
            if (!entry.allowed() || copying.contains(entry.key)) return;
            if (entry.target.isFile()) {
                Utilities.globalQueue.postRunnable(() -> writeSnapshot(entry));
                return;
            }
            pending.putIfAbsent(entry.key, entry);
            Entry waiting = pending.get(entry.key);
            // Open both encrypted cache and key before the normal expiry task can remove them.
            if (tryCopy(waiting, null)) return;
            AndroidUtilities.runOnUIThread(() -> {
                if (observed.add(entry.account)) {
                    NotificationCenter.getInstance(entry.account).addObserver(observer, NotificationCenter.fileLoaded);
                }
                // Close the gap between the initial lookup and observer registration.
                tryCopy(waiting, null);
            });
            AndroidUtilities.runOnUIThread(() -> pending.remove(entry.key, waiting), WAIT_MS);
        } catch (Exception e) { FileLog.e(e); }
    }

    private static final NotificationCenter.NotificationCenterDelegate observer = (id, account, args) -> {
        if (id != NotificationCenter.fileLoaded || args.length < 2 || !(args[1] instanceof File)) return;
        File loaded = (File) args[1];
        for (Entry entry : new ArrayList<>(pending.values())) {
            if (entry.account == account && (loaded.getName().equals(entry.source.getName())
                    || loaded.getName().equals(entry.source.getName() + ".enc"))) tryCopy(entry, loaded);
        }
    };

    private static boolean tryCopy(Entry entry, File loaded) {
        if (entry == null) return false;
        if (!entry.allowed()) { pending.remove(entry.key, entry); return false; }
        if (entry.target.isFile()) { pending.remove(entry.key, entry); return true; }
        File encrypted = new File(entry.source.getAbsolutePath() + ".enc");
        File source = loaded != null ? loaded : encrypted.isFile() ? encrypted : entry.source;
        if (!source.isFile() || source.length() <= 0 || !copying.add(entry.key)) return false;
        final InputStream input;
        final long length = source.length();
        try {
            input = source.getName().endsWith(".enc")
                    ? new EncryptedFileInputStream(source,
                        new File(FileLoader.getInternalCacheDir(), source.getName() + ".key"))
                    : new FileInputStream(source);
        } catch (Exception e) {
            copying.remove(entry.key); FileLog.e(e); return false;
        }
        pending.remove(entry.key, entry);
        Utilities.globalQueue.postRunnable(() -> {
            File temp = null;
            try (InputStream stream = input) {
                if (!entry.allowed()) return;
                File folder = entry.target.getParentFile();
                if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("Archive unavailable");
                temp = File.createTempFile("copy_", ".part", folder);
                long copied = 0;
                try (FileOutputStream output = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while (copied < length && (count = stream.read(buffer, 0, (int) Math.min(buffer.length, length - copied))) > 0) {
                        output.write(buffer, 0, count); copied += count;
                    }
                }
                if (copied != length) throw new java.io.IOException("Incomplete once media copy");
                if (entry.allowed() && !entry.target.exists() && !temp.renameTo(entry.target)) {
                    throw new java.io.IOException("Could not publish once media copy");
                }
                if (entry.allowed() && entry.target.isFile()) writeSnapshot(entry);
            } catch (Exception e) { FileLog.e(e); }
            finally {
                if (temp != null) temp.delete();
                copying.remove(entry.key);
            }
        });
        return true;
    }

    private static java.util.Set<String> snapshotIndex(int account) {
        long uid = UserConfig.getInstance(account).getClientUserId();
        return snapshots.computeIfAbsent(uid, key -> {
            java.util.Set<String> names = ConcurrentHashMap.newKeySet();
            File[] files = directory(account).listFiles((dir, name) -> name.endsWith(".tl"));
            if (files != null) for (File file : files) names.add(file.getName().substring(0, file.getName().length() - 3));
            return names;
        });
    }

    private static void writeAtomic(File file, byte[] bytes) throws Exception {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream stream = null;
        try {
            stream = atomic.startWrite(); stream.write(bytes); atomic.finishWrite(stream);
        } catch (Exception e) {
            if (stream != null) atomic.failWrite(stream);
            throw e;
        }
    }

    private static void writeSnapshot(Entry entry) {
        if (!entry.allowed() || !entry.target.isFile()) return;
        try {
            SerializedData envelope = new SerializedData();
            envelope.writeInt32(0x4c4f4e43);
            envelope.writeInt64(entry.uid);
            envelope.writeInt64(entry.dialogId);
            envelope.writeByteArray(entry.snapshot);
            byte[] bytes = envelope.toByteArray();
            envelope.cleanup();
            writeAtomic(new File(entry.target.getParentFile(), entry.base + ".tl"), bytes);
            snapshotIndex(entry.account).add(entry.base);
            if (viewed.contains(entry.uid + ":" + entry.base)) notifyRestored(entry.account, entry.dialogId, entry.messageId);
        } catch (Exception e) { FileLog.e(e); }
    }

    /** Called only when the first viewer is closed, never when it is merely opened. */
    public static void markViewed(int account, long dialogId, int messageId) {
        long uid = UserConfig.getInstance(account).getClientUserId();
        if (uid <= 0) return;
        String base = dialogId + "_" + messageId;
        String key = uid + ":" + base;
        if (!NimarkoConfig.keepOpenedOnceMedia && !snapshotIndex(account).contains(base)) return;
        viewed.add(key);
        File folder = directory(account);
        Utilities.globalQueue.postRunnable(() -> {
            if (UserConfig.getInstance(account).getClientUserId() != uid) return;
            // Serial queue: any copy started by opening the viewer finishes before this marker.
            if (!snapshotIndex(account).contains(base)) return;
            try {
                writeAtomic(new File(folder, base + ".viewed"), new byte[]{1});
                notifyRestored(account, dialogId, messageId);
            } catch (Exception e) { FileLog.e(e); }
        });
    }

    private static TLRPC.Message readSnapshot(int account, long dialogId, int messageId) {
        String base = dialogId + "_" + messageId;
        if (!snapshotIndex(account).contains(base)) return null;
        long uid = UserConfig.getInstance(account).getClientUserId();
        String key = uid + ":" + base;
        if (!viewed.contains(key)) {
            if (!new File(directory(account), base + ".viewed").isFile()) return null;
            viewed.add(key);
        }
        try {
            byte[] bytes = snapshotBytes.get(key);
            if (bytes == null) {
                File file = new File(directory(account), base + ".tl");
                long size = file.length();
                if (size <= 0 || size > 2 * 1024 * 1024) return null;
                bytes = new byte[(int) size];
                try (java.io.DataInputStream input = new java.io.DataInputStream(new FileInputStream(file))) { input.readFully(bytes); }
                if (snapshotBytes.size() >= 128) snapshotBytes.clear();
                snapshotBytes.put(key, bytes);
            }
            SerializedData data = new SerializedData(bytes);
            TLRPC.Message original;
            try {
                if (data.readInt32(true) != 0x4c4f4e43 || data.readInt64(true) != uid || data.readInt64(true) != dialogId) return null;
                SerializedData payload = new SerializedData(data.readByteArray(true));
                try { original = TLRPC.Message.TLdeserialize(payload, payload.readInt32(true), true); }
                finally { payload.cleanup(); }
            } finally { data.cleanup(); }
            if (original == null || original.id != messageId) return null;
            // Secret-chat dialog_id is a local field and is not included in the Telegram object serialization.
            original.dialog_id = dialogId;
            return original;
        } catch (Exception e) { FileLog.e(e); return null; }
    }

    /** Applies a local rendering overlay. No Telegram message is edited or re-sent. */
    public static boolean restoreMedia(int account, TLRPC.Message message) {
        if (message == null || UserConfig.getInstance(account).getClientUserId() <= 0) return false;
        long dialogId = MessageObject.getDialogId(message);
        TLRPC.Message original = readSnapshot(account, dialogId, message.id);
        if (original == null || original.media == null) return false;
        File folder = directory(account);
        String base = dialogId + "_" + message.id;
        File copy = null;
        for (String ext : new String[]{".jpg", ".mp4", ".ogg", ".gif"}) {
            File candidate = new File(folder, base + ext);
            if (candidate.isFile()) { copy = candidate; break; }
        }
        if (copy == null) return false;
        message.media = original.media;
        message.media.ttl_seconds = 0;
        message.media.flags &= ~4; // FLAG_2 is ttl_seconds in both photo and document media.
        message.ttl = 0; message.destroyTime = 0; message.destroyTimeMillis = 0;
        message.media_unread = false;
        message.attachPath = copy.getAbsolutePath();
        message.message = original.message;
        message.entities = original.entities;
        return true;
    }

    private static void notifyRestored(int account, long dialogId, int messageId) {
        AndroidUtilities.runOnUIThread(() -> {
            TLRPC.Message message = readSnapshot(account, dialogId, messageId);
            if (message != null && restoreMedia(account, message)) {
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateMessageMedia, message);
            }
        });
    }

    public static void forget(int account, File copy) {
        String name = copy.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return;
        String base = name.substring(0, dot);
        long uid;
        try { uid = Long.parseLong(copy.getParentFile().getName()); }
        catch (NumberFormatException e) { return; }
        java.util.Set<String> index = snapshots.get(uid);
        if (index != null) index.remove(base);
        viewed.remove(uid + ":" + base); snapshotBytes.remove(uid + ":" + base);
        new File(copy.getParentFile(), base + ".tl").delete();
        new File(copy.getParentFile(), base + ".viewed").delete();
    }

    public static void clear(int account, Runnable done) {
        long uid = UserConfig.getInstance(account).getClientUserId();
        generation(uid).incrementAndGet();
        snapshots.remove(uid);
        viewed.removeIf(key -> key.startsWith(uid + ":"));
        snapshotBytes.keySet().removeIf(key -> key.startsWith(uid + ":"));
        for (Entry entry : new ArrayList<>(pending.values())) {
            if (entry.uid == uid) pending.remove(entry.key, entry);
        }
        File folder = directory(account);
        Utilities.globalQueue.postRunnable(() -> {
            File[] files = folder.listFiles();
            if (files != null) for (File file : files) file.delete();
            AndroidUtilities.runOnUIThread(done);
        });
    }

    private OnceMediaArchive() { }
}
