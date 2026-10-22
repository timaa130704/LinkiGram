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

/** Copies only media the user opens; never changes Telegram read/deletion requests. */
public final class OnceMediaArchive {
    private static final ConcurrentHashMap<String, Entry> pending = new ConcurrentHashMap<>();
    private static final java.util.Set<String> copying = ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Integer> observed = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<Long, AtomicLong> generations = new ConcurrentHashMap<>();
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
        final String key;
        Entry(MessageObject message) {
            account = message.currentAccount;
            uid = UserConfig.getInstance(account).getClientUserId();
            generation = generation(uid).get();
            File attachment = message.messageOwner.attachPath == null || message.messageOwner.attachPath.isEmpty()
                    ? null : new File(message.messageOwner.attachPath);
            source = attachment != null && attachment.isFile() ? attachment
                    : FileLoader.getInstance(account).getPathToMessage(message.messageOwner);
            TLRPC.Document document = message.getDocument();
            String mime = document == null ? "" : document.mime_type;
            String ext = message.isVoiceOnce() ? ".ogg" : message.isRoundOnce() || message.isVideo()
                    || "video/mp4".equals(mime) ? ".mp4" : "image/gif".equals(mime) ? ".gif" : ".jpg";
            target = new File(directory(account), message.getDialogId() + "_" + message.getId() + ext);
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
            if (!entry.allowed() || entry.target.isFile() || copying.contains(entry.key)) return;
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
            } catch (Exception e) { FileLog.e(e); }
            finally {
                if (temp != null) temp.delete();
                copying.remove(entry.key);
            }
        });
        return true;
    }

    public static void clear(int account, Runnable done) {
        long uid = UserConfig.getInstance(account).getClientUserId();
        generation(uid).incrementAndGet();
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
