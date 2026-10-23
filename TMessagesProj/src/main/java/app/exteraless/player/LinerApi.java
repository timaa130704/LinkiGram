package app.exteraless.player;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.util.Base64;
import org.json.JSONObject;
import org.json.JSONArray;
import org.telegram.messenger.ApplicationLoader;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Liner desktop API contract: email/password login, rotating refresh tokens, SSE lyrics. */
public final class LinerApi {
    private static final String ORIGIN = "https://api.tryliner.fun";
    private static final String KEY = "linkigram.liner.session.v1";
    private static JSONObject session;
    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(KEY)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            return generator.generateKey();
        }
        return (SecretKey) store.getKey(KEY, null);
    }
    private static AtomicFile file() { return new AtomicFile(new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "liner-session")); }
    private static synchronized JSONObject session() {
        if (session != null) return session;
        try {
            JSONObject saved = new JSONObject(new String(file().readFully(), StandardCharsets.UTF_8));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(saved.getString("iv"), Base64.NO_WRAP)));
            session = new JSONObject(new String(cipher.doFinal(Base64.decode(saved.getString("data"), Base64.NO_WRAP)), StandardCharsets.UTF_8));
        } catch (Exception ignore) { session = new JSONObject(); }
        return session;
    }
    private static synchronized void save(JSONObject value) throws Exception {
        if (value.optString("accessToken").isEmpty() || value.optString("refreshToken").isEmpty()) throw new IOException("Invalid login response");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        JSONObject encrypted = new JSONObject().put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .put("data", Base64.encodeToString(cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP));
        AtomicFile target = file(); FileOutputStream output = target.startWrite();
        try { output.write(encrypted.toString().getBytes(StandardCharsets.UTF_8)); target.finishWrite(output); }
        catch (Exception error) { target.failWrite(output); throw error; }
        session = value;
    }
    public static synchronized boolean signedIn() { return !session().optString("accessToken").isEmpty(); }
    public static synchronized void logout() { session = new JSONObject(); file().delete(); }
    public static void login(String email, String password) throws Exception {
        save(json("/v1/auth/login", new JSONObject().put("email", email).put("password", password), null));
    }
    private static JSONObject json(String path, JSONObject body, String token) throws Exception {
        HttpURLConnection connection = open(path, token);
        if (path.startsWith("/v1/me/")) {
            connection.setConnectTimeout(4000); connection.setReadTimeout(5000);
        }
        try {
            if (body != null) {
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Liner HTTP " + status);
            try (InputStream input = connection.getInputStream()) { return new JSONObject(read(input, 2 * 1024 * 1024)); }
        } finally { connection.disconnect(); }
    }
    private static HttpURLConnection open(String path, String token) throws Exception {
        HttpURLConnection connection = LyricsDeadline.track((HttpURLConnection) new URL(ORIGIN + path).openConnection());
        connection.setConnectTimeout(12000); connection.setReadTimeout(15000); connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json, text/event-stream");
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
        return connection;
    }
    private static String read(InputStream input, int max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        while ((n = input.read(buffer)) != -1) { if (out.size() + n > max) throw new IOException("Liner response too large"); out.write(buffer, 0, n); }
        return out.toString("UTF-8");
    }
    private static String token(boolean refresh) throws Exception {
        JSONObject current = session();
        if (refresh) {
            String previous = current.getString("refreshToken");
            JSONObject updated = json("/v1/auth/refresh", new JSONObject().put("refreshToken", previous), null);
            synchronized (LinerApi.class) {
                if (previous.equals(session().optString("refreshToken"))) save(updated);
            }
        }
        return session().optString("accessToken");
    }
    private static String enc(String value) throws Exception { return URLEncoder.encode(value, "UTF-8"); }
    private static JSONObject authorizedJson(String path) throws Exception {
        try { return json(path, null, token(false)); }
        catch (IOException error) {
            if (!"Liner HTTP 401".equals(error.getMessage())) throw error;
            return json(path, null, token(true));
        }
    }

    private static LinerCatalog.Track accountTrack(OnlineLyrics.Query query) {
        ArrayList<LinerCatalog.Track> tracks = new ArrayList<>();
        String cursor = null;
        for (int pageNumber = 0; pageNumber < 3; pageNumber++) {
            try {
                String path = "/v1/me/history?limit=30" + (cursor == null ? "" : "&cursor=" + enc(cursor));
                JSONObject page = authorizedJson(path);
                collectAccountTracks(page, tracks);
                LinerCatalog.Track matched = LinerCatalog.match(tracks, query);
                if (matched != null) {
                    android.util.Log.i("LinkiLyrics", "Liner account history track matched");
                    return matched;
                }
                cursor = page.optString("nextCursor", "");
                if (cursor.isEmpty() || "null".equals(cursor)) break;
            } catch (Exception error) {
                android.util.Log.w("LinkiLyrics", "Liner account history lookup failed: " + error.getMessage());
                break;
            }
        }
        try {
            JSONObject page = authorizedJson("/v1/me/library/tracks?limit=100");
            collectAccountTracks(page, tracks);
            LinerCatalog.Track matched = LinerCatalog.match(tracks, query);
            if (matched != null) {
                android.util.Log.i("LinkiLyrics", "Liner account library track matched");
                return matched;
            }
        } catch (Exception error) {
            android.util.Log.w("LinkiLyrics", "Liner account library lookup failed: " + error.getMessage());
        }
        return null;
    }

    private static void collectAccountTracks(JSONObject page, ArrayList<LinerCatalog.Track> tracks) {
        JSONArray items = page.optJSONArray("items");
        if (items == null) return;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            JSONObject track = item == null ? null : item.optJSONObject("track");
            if (track == null) continue;
            JSONArray artists = track.optJSONArray("artists");
            String first = ""; StringBuilder all = new StringBuilder();
            if (artists != null) for (int j = 0; j < artists.length(); j++) {
                JSONObject artist = artists.optJSONObject(j);
                String name = artist == null ? "" : artist.optString("name");
                if (name.isEmpty()) continue;
                if (first.isEmpty()) first = name;
                if (all.length() > 0) all.append(' ');
                all.append(name);
            }
            String id = track.optString("id");
            if (!id.isEmpty()) tracks.add(new LinerCatalog.Track(id, track.optString("title"),
                    all.toString(), first, track.optLong("durationMs")));
        }
    }

    interface Listener { void onLyrics(Lyrics lyrics); }

    public static ArrayList<Lyrics> lyrics(OnlineLyrics.Query query) throws Exception {
        return lyrics(query, null);
    }

    static ArrayList<Lyrics> lyrics(OnlineLyrics.Query query, Listener listener) throws Exception {
        ArrayList<Lyrics> result = new ArrayList<>();
        if (!signedIn() || query.artist == null) return result;
        // Catalog-backed providers need the same real track ID used by Liner desktop.
        LinerCatalog.Track track = accountTrack(query);
        if (track == null) track = LinerCatalog.resolve(query);
        String id = track == null ? null : track.id;
        if (id == null) id = "linkigram-" + org.telegram.messenger.Utilities.MD5(query.key());
        String title = track == null ? query.originalTitle : track.title;
        String artist = track == null ? query.artist : track.hintArtist;
        long durationMs = track != null && track.durationMs > 0 ? track.durationMs : query.duration * 1000L;
        String path = "/v1/tracks/" + enc(id) + "/lyrics?title=" + enc(title) + "&artist=" + enc(artist)
                + (durationMs > 0 ? "&durationMs=" + durationMs : "");
        android.util.Log.i("LinkiLyrics", "Liner catalog match=" + (track != null));
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection connection = open(path, token(attempt > 0));
            try {
                // Liner streams provider results independently; slow providers can arrive well after the first candidate.
                connection.setReadTimeout(55000);
                connection.setRequestProperty("x-request-id", java.util.UUID.randomUUID().toString());
                connection.setRequestProperty("x-client-version", org.telegram.messenger.BuildVars.BUILD_VERSION_STRING);
                connection.setRequestProperty("x-platform", "android");
                connection.setRequestProperty("x-session-id", id);
                int status = connection.getResponseCode();
                if (status == 401 && attempt == 0) continue;
                if (status != 200) throw new IOException("Liner HTTP " + status);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; StringBuilder event = new StringBuilder(); int total = 0; long deadline = System.currentTimeMillis() + 300000;
                    while ((line = reader.readLine()) != null) {
                        total += line.length(); if (total > 4 * 1024 * 1024 || System.currentTimeMillis() > deadline) throw new IOException("Liner stream limit");
                        if (line.isEmpty()) {
                            if (addEvent(event.toString(), result, durationMs, listener)) return result;
                            event.setLength(0);
                        }
                        else if (line.startsWith("data:")) event.append(line.substring(5).trim()).append("\n");
                    }
                    addEvent(event.toString(), result, durationMs, listener);
                } catch (IOException error) {
                    if (result.isEmpty()) throw error;
                    org.telegram.messenger.FileLog.e("Liner stream interrupted; keeping completed providers", error);
                }
                return result;
            } finally { connection.disconnect(); }
        }
        return result;
    }
    private static boolean addEvent(String raw, ArrayList<Lyrics> out, long durationMs, Listener listener) {
        try {
            JSONObject event = new JSONObject(raw);
            if ("done".equals(event.optString("type"))) return true;
            if (!"provider".equals(event.optString("type"))) return false;
            android.util.Log.i("LinkiLyrics", "Liner provider=" + event.optString("provider") + " status=" + event.optString("status"));
            if (!"found".equals(event.optString("status"))) return false;
            JSONObject candidate = event.getJSONObject("candidate");
            JSONObject lyrics = candidate.getJSONObject("lyrics");
            Lyrics parsed = LinerLyricsParser.parse(lyrics.getString("content"), lyrics.optString("format"),
                    "Liner · " + candidate.optString("provider", event.optString("provider")), durationMs);
            if (parsed != null) {
                parsed.providerTopPriority = candidate.optBoolean("topPriority");
                JSONObject quality = candidate.optJSONObject("quality");
                parsed.providerQuality = quality == null ? 0 : quality.optDouble("total", 0);
                String sync = candidate.optString("syncLevel", event.optString("syncLevel"));
                parsed.providerSyncRank = "word_level".equals(sync) ? 3 : "syllable_level".equals(sync) ? 2 : "line_level".equals(sync) ? 1 : 0;
                android.util.Log.i("LinkiLyrics", "Liner parsed provider=" + candidate.optString("provider", event.optString("provider"))
                        + " format=" + lyrics.optString("format") + " lines=" + parsed.lines.size());
                for (int i = out.size() - 1; i >= 0; i--) {
                    if (parsed.provider.equals(out.get(i).provider)) out.remove(i);
                }
                out.add(parsed);
                if (listener != null) listener.onLyrics(parsed);
            } else {
                android.util.Log.w("LinkiLyrics", "Liner unsupported lyrics format: " + lyrics.optString("format")
                        + " provider=" + candidate.optString("provider", event.optString("provider")));
            }
        } catch (Exception error) {
            if (!raw.trim().isEmpty()) org.telegram.messenger.FileLog.e("Liner lyrics event could not be parsed", error);
        }
        return false;
    }
    private LinerApi() { }
}
