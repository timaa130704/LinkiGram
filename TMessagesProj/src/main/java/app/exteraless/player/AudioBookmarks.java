package app.exteraless.player;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.MessageObject;

import app.nimarkogram.messenger.NimarkoConfig;

import java.util.ArrayList;
import java.util.List;

/** Local playback bookmarks, scoped to the source message (not the account's credentials). */
final class AudioBookmarks {
    static final class Bookmark {
        final long positionMs;
        final String title;

        Bookmark(long positionMs, String title) {
            this.positionMs = positionMs;
            this.title = title;
        }
    }

    private static String key(MessageObject message) {
        if (message == null || message.messageOwner == null) return null;
        return "audioBookmarks_" + message.currentAccount + "_" + message.messageOwner.dialog_id + "_" + message.messageOwner.id;
    }

    static List<Bookmark> get(MessageObject message) {
        String key = key(message);
        ArrayList<Bookmark> result = new ArrayList<>();
        if (key == null) return result;
        try {
            JSONArray array = new JSONArray(NimarkoConfig.getPreferences().getString(key, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) result.add(new Bookmark(Math.max(0, item.optLong("position")), item.optString("title", "")));
            }
        } catch (Exception ignore) { }
        return result;
    }

    static boolean add(MessageObject message, long positionMs) {
        String key = key(message);
        if (key == null || positionMs < 0) return false;
        List<Bookmark> items = get(message);
        for (Bookmark item : items) if (Math.abs(item.positionMs - positionMs) < 1500) return false;
        if (items.size() >= 100) return false;
        items.add(new Bookmark(positionMs, message.getMusicTitle()));
        JSONArray array = new JSONArray();
        for (Bookmark item : items) {
            JSONObject json = new JSONObject();
            try {
                json.put("position", item.positionMs);
                json.put("title", TextUtils.isEmpty(item.title) ? "" : item.title);
                array.put(json);
            } catch (Exception ignore) { }
        }
        NimarkoConfig.getPreferences().edit().putString(key, array.toString()).apply();
        return true;
    }
}
