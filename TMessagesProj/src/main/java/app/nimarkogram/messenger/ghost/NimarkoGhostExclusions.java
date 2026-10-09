/*
 * Per-chat ghost mode exclusions, ported from AyuGram for Android
 * (Copyright @Radolyn, 2023) via exteraless. GPL code port.
 */
package app.nimarkogram.messenger.ghost;

import android.content.SharedPreferences;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import app.nimarkogram.messenger.NimarkoConfig;

public class NimarkoGhostExclusions {

    public static final String readExclusionPrefix = "ghostModeReadExclusion_";
    public static final String typingExclusionPrefix = "ghostModeTypingExclusion_";

    private static final ConcurrentHashMap<Long, Boolean> readExclusions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> typingExclusions = new ConcurrentHashMap<>();

    public static void setReadExclusion(long chatId, boolean value) {
        long key = getKey(chatId);
        readExclusions.put(key, value);
        NimarkoConfig.getEditor().putBoolean(readExclusionPrefix + key, value).apply();
    }

    public static boolean getReadExclusion(long chatId) {
        long key = getKey(chatId);
        return readExclusions.computeIfAbsent(key, k ->
                NimarkoConfig.getPreferences().getBoolean(readExclusionPrefix + k, false)
        );
    }

    public static void setTypingExclusion(long chatId, boolean value) {
        long key = getKey(chatId);
        typingExclusions.put(key, value);
        NimarkoConfig.getEditor().putBoolean(typingExclusionPrefix + key, value).apply();
    }

    public static boolean getTypingExclusion(long chatId) {
        long key = getKey(chatId);
        return typingExclusions.computeIfAbsent(key, k ->
                NimarkoConfig.getPreferences().getBoolean(typingExclusionPrefix + k, false)
        );
    }

    private static long getKey(long chatId) {
        return chatId;
    }

    /** Load all persisted exclusions eagerly (call once after config init). */
    public static void load() {
        readExclusions.clear();
        typingExclusions.clear();
        SharedPreferences prefs = NimarkoConfig.getPreferences();
        Map<String, ?> all = prefs.getAll();
        if (all == null) {
            return;
        }
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String name = entry.getKey();
            if (!(entry.getValue() instanceof Boolean)) {
                continue;
            }
            boolean value = (Boolean) entry.getValue();
            if (name.startsWith(readExclusionPrefix)) {
                readExclusions.put(parseKey(name, readExclusionPrefix), value);
            } else if (name.startsWith(typingExclusionPrefix)) {
                typingExclusions.put(parseKey(name, typingExclusionPrefix), value);
            }
        }
    }

    private static long parseKey(String name, String prefix) {
        try {
            return Long.parseLong(name.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }
}
