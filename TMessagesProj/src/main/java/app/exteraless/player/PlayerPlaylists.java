package app.exteraless.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Local, in-app music playlists / favourites for the MD3 player. Everything is
 * stored as JSON in a private SharedPreferences file and never leaves the
 * device. An entry keeps only the identifiers needed to resolve the track back
 * to a {@link MessageObject} later.
 */
public final class PlayerPlaylists {

    private static final String PREFS_NAME = "oe_player_playlists";
    private static final String KEY = "playlists";
    private static final Object LOCK = new Object();

    private PlayerPlaylists() {
    }

    public static final class Entry {
        public final int account;
        public final long dialogId;
        public final int messageId;
        public final String title;
        public final String author;

        Entry(int account, long dialogId, int messageId, String title, String author) {
            this.account = account;
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.title = title;
            this.author = author;
        }
    }

    public static final class Playlist {
        public long id;
        public String name;
        public final ArrayList<Entry> entries = new ArrayList<>();
    }

    public static List<Playlist> list() {
        synchronized (LOCK) {
            return new ArrayList<>(load());
        }
    }

    public static Playlist create(String name) {
        synchronized (LOCK) {
            ArrayList<Playlist> all = load();
            Playlist playlist = new Playlist();
            long id = 1;
            for (int i = 0; i < all.size(); i++) {
                id = Math.max(id, all.get(i).id + 1);
            }
            playlist.id = id;
            playlist.name = TextUtils.isEmpty(name) ? "" : name.trim();
            all.add(playlist);
            save(all);
            return playlist;
        }
    }

    public static void rename(long id, String name) {
        synchronized (LOCK) {
            ArrayList<Playlist> all = load();
            for (int i = 0; i < all.size(); i++) {
                Playlist playlist = all.get(i);
                if (playlist.id == id) {
                    playlist.name = TextUtils.isEmpty(name) ? playlist.name : name.trim();
                    save(all);
                    return;
                }
            }
        }
    }

    public static void delete(long id) {
        synchronized (LOCK) {
            ArrayList<Playlist> all = load();
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id == id) {
                    all.remove(i);
                    save(all);
                    return;
                }
            }
        }
    }

    public static boolean addTrack(long playlistId, MessageObject messageObject) {
        if (messageObject == null) {
            return false;
        }
        synchronized (LOCK) {
            ArrayList<Playlist> all = load();
            for (int i = 0; i < all.size(); i++) {
                Playlist playlist = all.get(i);
                if (playlist.id != playlistId) {
                    continue;
                }
                for (int j = 0; j < playlist.entries.size(); j++) {
                    Entry e = playlist.entries.get(j);
                    if (e.account == messageObject.currentAccount && e.dialogId == messageObject.getDialogId() && e.messageId == messageObject.getId()) {
                        return false;
                    }
                }
                playlist.entries.add(new Entry(
                        messageObject.currentAccount,
                        messageObject.getDialogId(),
                        messageObject.getId(),
                        messageObject.getMusicTitle(),
                        messageObject.getMusicAuthor()));
                save(all);
                return true;
            }
            return false;
        }
    }

    public static void removeEntry(long playlistId, int index) {
        synchronized (LOCK) {
            ArrayList<Playlist> all = load();
            for (int i = 0; i < all.size(); i++) {
                Playlist playlist = all.get(i);
                if (playlist.id == playlistId && index >= 0 && index < playlist.entries.size()) {
                    playlist.entries.remove(index);
                    save(all);
                    return;
                }
            }
        }
    }

    public static boolean contains(long playlistId, MessageObject messageObject) {
        if (messageObject == null) {
            return false;
        }
        synchronized (LOCK) {
            ArrayList<Playlist> all = load();
            for (int i = 0; i < all.size(); i++) {
                Playlist playlist = all.get(i);
                if (playlist.id != playlistId) {
                    continue;
                }
                for (int j = 0; j < playlist.entries.size(); j++) {
                    Entry e = playlist.entries.get(j);
                    if (e.account == messageObject.currentAccount && e.dialogId == messageObject.getDialogId() && e.messageId == messageObject.getId()) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static ArrayList<Playlist> load() {
        ArrayList<Playlist> out = new ArrayList<>();
        String raw = prefs().getString(KEY, null);
        if (TextUtils.isEmpty(raw)) {
            return out;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                Playlist playlist = new Playlist();
                playlist.id = obj.optLong("id");
                playlist.name = obj.optString("name", "");
                JSONArray entries = obj.optJSONArray("entries");
                for (int j = 0; entries != null && j < entries.length(); j++) {
                    JSONObject e = entries.optJSONObject(j);
                    if (e == null) {
                        continue;
                    }
                    playlist.entries.add(new Entry(
                            e.optInt("account"),
                            e.optLong("dialogId"),
                            e.optInt("messageId"),
                            e.optString("title", ""),
                            e.optString("author", "")));
                }
                out.add(playlist);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void save(ArrayList<Playlist> all) {
        try {
            JSONArray array = new JSONArray();
            for (int i = 0; i < all.size(); i++) {
                Playlist playlist = all.get(i);
                JSONObject obj = new JSONObject();
                obj.put("id", playlist.id);
                obj.put("name", playlist.name);
                JSONArray entries = new JSONArray();
                for (int j = 0; j < playlist.entries.size(); j++) {
                    Entry e = playlist.entries.get(j);
                    JSONObject entry = new JSONObject();
                    entry.put("account", e.account);
                    entry.put("dialogId", e.dialogId);
                    entry.put("messageId", e.messageId);
                    entry.put("title", e.title);
                    entry.put("author", e.author);
                    entries.put(entry);
                }
                obj.put("entries", entries);
                array.put(obj);
            }
            prefs().edit().putString(KEY, array.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}