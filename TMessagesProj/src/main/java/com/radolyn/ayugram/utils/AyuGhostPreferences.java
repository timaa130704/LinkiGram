package com.radolyn.ayugram.utils;

import java.util.concurrent.ConcurrentHashMap;

import app.nimarkogram.messenger.NimarkoConfig;

public class AyuGhostPreferences {
    public static final String ghostReadExclusionPrefix = "ghostModeReadExclusion_";
    public static final String ghostTypingExclusionPrefix = "ghostModeTypingExclusion_";
    private static final ConcurrentHashMap<Long, Boolean> ghostModeReadExclusions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> ghostModeTypingExclusions = new ConcurrentHashMap<>();

    public static void setGhostModeReadExclusion(long chatId, boolean value) {
        long key = Math.abs(chatId);
        ghostModeReadExclusions.put(key, value);
        app.nimarkogram.messenger.NimarkoConfig.getPreferences().edit().putBoolean(ghostReadExclusionPrefix + key, value).apply();
    }

    public static boolean getGhostModeReadExclusion(long chatId) {
        long key = Math.abs(chatId);
        return ghostModeReadExclusions.computeIfAbsent(key, k ->
                app.nimarkogram.messenger.NimarkoConfig.getPreferences().getBoolean(ghostReadExclusionPrefix + k, false)
        );
    }

    public static void setGhostModeTypingExclusion(long chatId, boolean value) {
        long key = Math.abs(chatId);
        ghostModeTypingExclusions.put(key, value);
        app.nimarkogram.messenger.NimarkoConfig.getPreferences().edit().putBoolean(ghostTypingExclusionPrefix + key, value).apply();
    }

    public static boolean getGhostModeTypingExclusion(long chatId) {
        long key = Math.abs(chatId);
        return ghostModeTypingExclusions.computeIfAbsent(key, k ->
                app.nimarkogram.messenger.NimarkoConfig.getPreferences().getBoolean(ghostTypingExclusionPrefix + k, false)
        );
    }

    private static String normalChatKey(int account, long dialogId) {
        return "ghostNormalChat_" + org.telegram.messenger.UserConfig.getInstance(account).getClientUserId() + "_" + dialogId;
    }

    public static boolean isNormalChat(int account, long dialogId) {
        return NimarkoConfig.getPreferences().getBoolean(normalChatKey(account, dialogId), false);
    }

    public static void setNormalChat(int account, long dialogId, boolean value) {
        NimarkoConfig.getPreferences().edit().putBoolean(normalChatKey(account, dialogId), value).apply();
    }

    public static boolean isReadExcluded(int account, long dialogId) {
        String key = normalChatKey(account, dialogId);
        return NimarkoConfig.getPreferences().contains(key) ? isNormalChat(account, dialogId)
                : getGhostModeReadExclusion(dialogId);
    }

    public static boolean isTypingExcluded(int account, long dialogId) {
        String key = normalChatKey(account, dialogId);
        return NimarkoConfig.getPreferences().contains(key) ? isNormalChat(account, dialogId)
                : getGhostModeTypingExclusion(dialogId);
    }

}
