package org.telegram.messenger.sessions;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** SAF streams only; no storage permission, filesystem URI resolution or external staging. */
public final class SessionFiles {
    public static final int TELETHON_FILE = 0, PYROGRAM_FILE = 1, TDATA = 2,
            TELETHON_STRING = 3, PYROGRAM_STRING = 4, AUTH_KEY = 5;
    private static final int MAX_FILE = 16 * 1024 * 1024;
    private SessionFiles() {}

    public static List<SessionData> read(Context context, Uri uri, boolean tdata, String passcode) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open document");
            if (tdata) return TdataCodec.read(readZip(in), passcode);
            File tmp = File.createTempFile("session-import-", ".session", context.getCacheDir());
            try {
                try (OutputStream out = new FileOutputStream(tmp)) { copy(in, out, MAX_FILE); }
                return Collections.singletonList(readDatabase(tmp));
            } finally { deleteDatabase(tmp); }
        }
    }

    private static SessionData readDatabase(File file) throws Exception {
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS)) {
            Set<String> columns = new HashSet<>();
            try (Cursor c = db.rawQuery("PRAGMA table_info(sessions)", null)) {
                while (c.moveToNext()) columns.add(c.getString(1));
            }
            boolean pyrogram = columns.contains("test_mode") && columns.contains("user_id");
            if (!columns.contains("dc_id") || !columns.contains("auth_key") || (!pyrogram && !columns.contains("server_address"))) throw new IOException("Unknown session schema");
            String query = pyrogram ? "SELECT dc_id, auth_key, user_id, test_mode, is_bot FROM sessions LIMIT 2"
                    : "SELECT dc_id, auth_key, server_address FROM sessions LIMIT 2";
            try (Cursor c = db.rawQuery(query, null)) {
                if (c.getCount() != 1 || !c.moveToFirst()) throw new IOException("Expected one session");
                if (pyrogram && (c.getInt(3) != 0 || c.getInt(4) != 0)) throw new IOException("Test/bot sessions are not supported");
                // Telethon has no test flag; reject the documented test endpoint addresses.
                if (!pyrogram && Arrays.asList("149.154.175.10", "149.154.167.40", "149.154.175.117").contains(c.getString(2))) throw new IOException("Test session");
                byte[] key = c.getBlob(1);
                try { return new SessionData(c.getInt(0), key, pyrogram ? c.getLong(2) : 0, 0); }
                finally { if (key != null) Arrays.fill(key, (byte) 0); }
            }
        }
    }

    public static void write(Context context, Uri uri, int format, SessionData session) throws Exception {
        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Cannot create document");
            if (format == TDATA) {
                try (ZipOutputStream zip = new ZipOutputStream(out)) {
                    for (Map.Entry<String, byte[]> entry : TdataCodec.write(session).entrySet()) {
                        zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry();
                    }
                }
            } else if (format == TELETHON_FILE || format == PYROGRAM_FILE) {
                File file = File.createTempFile("session-export-", ".session", context.getCacheDir());
                try {
                    writeDatabase(file, format, session);
                    try (InputStream in = new FileInputStream(file)) { copy(in, out, MAX_FILE); }
                } finally { deleteDatabase(file); }
            } else {
                String value = format == TELETHON_STRING ? StringSessionCodec.telethon(session)
                        : format == PYROGRAM_STRING ? StringSessionCodec.pyrogram(session) : StringSessionCodec.manual(session);
                out.write(value.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static void writeDatabase(File file, int format, SessionData s) {
        try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null)) {
            db.execSQL("PRAGMA journal_mode=DELETE");
            db.beginTransaction();
            try {
                if (format == TELETHON_FILE) {
                    // v7 is understood by stable Telethon 1.x and migrated by newer versions.
                    db.execSQL("CREATE TABLE version (version INTEGER PRIMARY KEY)"); db.execSQL("INSERT INTO version VALUES (7)");
                    db.execSQL("CREATE TABLE sessions (dc_id INTEGER PRIMARY KEY, server_address TEXT, port INTEGER, auth_key BLOB, takeout_id INTEGER)");
                    db.execSQL("CREATE TABLE entities (id INTEGER PRIMARY KEY, hash INTEGER NOT NULL, username TEXT, phone INTEGER, name TEXT, date INTEGER)");
                    db.execSQL("CREATE TABLE sent_files (md5_digest BLOB, file_size INTEGER, type INTEGER, id INTEGER, hash INTEGER, PRIMARY KEY(md5_digest,file_size,type))");
                    db.execSQL("CREATE TABLE update_state (id INTEGER PRIMARY KEY, pts INTEGER, qts INTEGER, date INTEGER, seq INTEGER)");
                    db.execSQL("INSERT INTO sessions VALUES (?, ?, 443, ?, NULL)", new Object[]{s.dcId, StringSessionCodec.host(s.dcId), s.authKey});
                } else {
                    db.execSQL("CREATE TABLE version (number INTEGER PRIMARY KEY)"); db.execSQL("INSERT INTO version VALUES (3)");
                    db.execSQL("CREATE TABLE sessions (dc_id INTEGER PRIMARY KEY, api_id INTEGER, test_mode INTEGER, auth_key BLOB, date INTEGER NOT NULL, user_id INTEGER, is_bot INTEGER)");
                    db.execSQL("CREATE TABLE peers (id INTEGER PRIMARY KEY, access_hash INTEGER, type INTEGER NOT NULL, username TEXT, phone_number TEXT, last_update_on INTEGER NOT NULL DEFAULT (CAST(STRFTIME('%s','now') AS INTEGER)))");
                    db.execSQL("CREATE INDEX idx_peers_id ON peers(id)"); db.execSQL("CREATE INDEX idx_peers_username ON peers(username)"); db.execSQL("CREATE INDEX idx_peers_phone_number ON peers(phone_number)");
                    db.execSQL("CREATE TRIGGER trg_peers_last_update_on AFTER UPDATE ON peers BEGIN UPDATE peers SET last_update_on=CAST(STRFTIME('%s','now') AS INTEGER) WHERE id=NEW.id; END");
                    db.execSQL("INSERT INTO sessions VALUES (?, ?, 0, ?, 0, ?, 0)", new Object[]{s.dcId, s.apiId, s.authKey, s.userId});
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
    }

    private static Map<String, byte[]> readZip(InputStream in) throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        int entries = 0, total = 0;
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 4096) throw new IOException("Too many ZIP entries");
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || Arrays.asList(name.split("/")).contains("..")) throw new IOException("Unsafe ZIP path");
                if (entry.isDirectory()) continue;
                String base = name.substring(name.lastIndexOf('/') + 1);
                boolean relevant = base.matches("key_data[s01]|[A-Fa-f0-9]{16}[s01]");
                ByteArrayOutputStream out = relevant ? new ByteArrayOutputStream() : null;
                byte[] buffer = new byte[8192]; int n, size = 0;
                while ((n = zip.read(buffer)) != -1) {
                    size += n; total += n;
                    if (total > 64 * 1024 * 1024 || (relevant && size > 2 * 1024 * 1024)) throw new IOException("ZIP exceeds size limit; remove cache files");
                    if (out != null) out.write(buffer, 0, n);
                }
                // Authorization files must be alongside key_data, with or without a tdata/ prefix.
                if (relevant) {
                    if (files.put(name, out.toByteArray()) != null) throw new IOException("Duplicate ZIP entry");
                }
            }
        }
        // Accept an enclosing backup directory, but never combine different tdata roots.
        String root = null;
        for (String name : files.keySet()) if (name.matches("(?:.*/)?key_data[s01]")) {
            String candidate = name.substring(0, name.lastIndexOf('/') + 1);
            if (root != null && !root.equals(candidate)) throw new IOException("Multiple tdata roots");
            root = candidate;
        }
        if (root == null) throw new IOException("key_data is missing");
        Map<String, byte[]> normalized = new HashMap<>();
        for (Map.Entry<String, byte[]> e : files.entrySet()) if (e.getKey().startsWith(root) && !e.getKey().substring(root.length()).contains("/")) normalized.put(e.getKey().substring(root.length()), e.getValue());
        return normalized;
    }

    private static void copy(InputStream in, OutputStream out, int max) throws IOException {
        byte[] buffer = new byte[8192]; int n, total = 0;
        while ((n = in.read(buffer)) != -1) { total += n; if (total > max) throw new IOException("Document too large"); out.write(buffer, 0, n); }
    }
    private static void deleteDatabase(File file) {
        SQLiteDatabase.deleteDatabase(file);
    }
}
