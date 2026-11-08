package org.telegram.messenger.sessions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/** Telethon v1 and Pyrogram v1/v2 wire layouts (network byte order). */
public final class StringSessionCodec {
    private static final String[] HOSTS = {"", "149.154.175.53", "149.154.167.51", "149.154.175.100", "149.154.167.91", "91.108.56.130"};
    private StringSessionCodec() {}

    public static String host(int dc) { return HOSTS[dc]; }

    public static SessionData telethon(String value) {
        value = value.trim();
        if (value.length() > 512 || !value.startsWith("1")) throw new IllegalArgumentException("Telethon version");
        byte[] raw = Base64.getUrlDecoder().decode(value.substring(1));
        if (raw.length != 263 && raw.length != 275) throw new IllegalArgumentException("Telethon length");
        ByteBuffer b = ByteBuffer.wrap(raw);
        int dc = b.get() & 255;
        b.position(1 + (raw.length == 263 ? 4 : 16) + 2);
        byte[] key = new byte[256];
        b.get(key);
        try { return new SessionData(dc, key, 0, 0); }
        finally { java.util.Arrays.fill(key, (byte) 0); java.util.Arrays.fill(raw, (byte) 0); }
    }

    public static SessionData pyrogram(String value) {
        value = value.trim();
        if (value.length() > 512) throw new IllegalArgumentException("Pyrogram length");
        byte[] raw = Base64.getUrlDecoder().decode(value);
        if (raw.length != 263 && raw.length != 267 && raw.length != 271) throw new IllegalArgumentException("Pyrogram length");
        ByteBuffer b = ByteBuffer.wrap(raw);
        int dc = b.get() & 255;
        int apiId = raw.length == 271 ? b.getInt() : 0;
        if (b.get() != 0) throw new IllegalArgumentException("Test sessions are not supported");
        byte[] key = new byte[256];
        b.get(key);
        long uid = raw.length == 263 ? Integer.toUnsignedLong(b.getInt()) : b.getLong();
        if (b.get() != 0) throw new IllegalArgumentException("Bot sessions are not supported");
        try { return new SessionData(dc, key, uid, apiId); }
        finally { java.util.Arrays.fill(key, (byte) 0); java.util.Arrays.fill(raw, (byte) 0); }
    }

    public static SessionData manual(String hex, String dc) {
        hex = hex.replaceAll("\\s", "");
        if (hex.length() != 512 || !hex.matches("[0-9a-fA-F]+")) throw new IllegalArgumentException("Auth key must be 512 hex characters");
        byte[] key = new byte[256];
        for (int i = 0; i < key.length; i++) key[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        try { return new SessionData(Integer.parseInt(dc.trim()), key, 0, 0); }
        finally { java.util.Arrays.fill(key, (byte) 0); }
    }

    public static String telethon(SessionData session) {
        String[] ip = host(session.dcId).split("\\.");
        ByteBuffer b = ByteBuffer.allocate(263);
        b.put((byte) session.dcId);
        for (String part : ip) b.put((byte) Integer.parseInt(part));
        b.putShort((short) 443).put(session.authKey);
        return "1" + Base64.getUrlEncoder().encodeToString(b.array());
    }

    public static String pyrogram(SessionData session) {
        if (session.userId == 0 || session.apiId <= 0) throw new IllegalArgumentException("Missing user/API ID");
        ByteBuffer b = ByteBuffer.allocate(271).order(ByteOrder.BIG_ENDIAN);
        b.put((byte) session.dcId).putInt(session.apiId).put((byte) 0).put(session.authKey).putLong(session.userId).put((byte) 0);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b.array());
    }

    public static String manual(SessionData session) {
        StringBuilder b = new StringBuilder(540);
        b.append("DC ID: ").append(session.dcId).append("\nAuth Key: ");
        char[] hex = "0123456789abcdef".toCharArray();
        for (byte x : session.authKey) b.append(hex[(x & 255) >>> 4]).append(hex[x & 15]);
        return b.toString();
    }
}
