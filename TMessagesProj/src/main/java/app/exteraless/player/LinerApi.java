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
        HttpURLConnection connection = (HttpURLConnection) new URL(ORIGIN + path).openConnection();
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
    private static String normalized(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
                .toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static String catalogTrackId(OnlineLyrics.Query query) {
        try {
            String path = "/v1/search?type=track&q=" + enc(query.artist + " " + query.title);
            JSONObject response;
            try {
                response = json(path, null, token(false));
            } catch (IOException error) {
                if (!"Liner HTTP 401".equals(error.getMessage())) throw error;
                response = json(path, null, token(true));
            }
            JSONArray items = response.optJSONArray("items");
            if (items == null) return null;
            String title = normalized(query.title);
            String artist = normalized(query.artist);
            String best = null;
            long bestDifference = Long.MAX_VALUE;
            for (int i = 0; i < items.length(); i++) {
                JSONObject track = items.optJSONObject(i);
                if (track == null || !title.equals(normalized(track.optString("title")))) continue;
                JSONArray artists = track.optJSONArray("artists");
                boolean artistMatches = false;
                StringBuilder allArtists = new StringBuilder();
                if (artists != null) for (int j = 0; j < artists.length(); j++) {
                    JSONObject item = artists.optJSONObject(j);
                    if (item == null) continue;
                    String name = normalized(item.optString("name"));
                    if (artist.equals(name)) artistMatches = true;
                    if (allArtists.length() > 0) allArtists.append(' ');
                    allArtists.append(name);
                }
                if (!artistMatches && !artist.equals(allArtists.toString())) continue;
                long duration = track.optLong("durationMs", 0);
                long difference = duration > 0 && query.duration > 0 ? Math.abs(duration - query.duration * 1000L) : 0;
                if (difference > 8000 || difference >= bestDifference) continue;
                String id = track.optString("id");
                if (!id.isEmpty()) { best = id; bestDifference = difference; }
            }
            return best;
        } catch (Exception error) {
            org.telegram.messenger.FileLog.e("Liner catalog matching failed; using metadata hints", error);
            return null;
        }
    }

    public static ArrayList<Lyrics> lyrics(OnlineLyrics.Query query) throws Exception {
        ArrayList<Lyrics> result = new ArrayList<>();
        if (!signedIn() || query.artist == null) return result;
        // Catalog-backed providers need the same real track ID used by Liner desktop.
        String id = catalogTrackId(query);
        if (id == null) id = "linkigram-" + org.telegram.messenger.Utilities.MD5(query.key());
        String path = "/v1/tracks/" + enc(id) + "/lyrics?title=" + enc(query.title) + "&artist=" + enc(query.artist) + "&durationMs=" + query.duration * 1000L;
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection connection = open(path, token(attempt > 0));
            try {
                connection.setReadTimeout(60000);
                connection.setRequestProperty("x-request-id", java.util.UUID.randomUUID().toString());
                connection.setRequestProperty("x-client-version", org.telegram.messenger.BuildVars.BUILD_VERSION_STRING);
                connection.setRequestProperty("x-platform", "android");
                connection.setRequestProperty("x-session-id", id);
                int status = connection.getResponseCode();
                if (status == 401 && attempt == 0) continue;
                if (status != 200) throw new IOException("Liner HTTP " + status);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; StringBuilder event = new StringBuilder(); int total = 0; long deadline = System.currentTimeMillis() + 120000;
                    while ((line = reader.readLine()) != null) {
                        total += line.length(); if (total > 4 * 1024 * 1024 || System.currentTimeMillis() > deadline) throw new IOException("Liner stream limit");
                        if (line.isEmpty()) {
                            if (addEvent(event.toString(), result)) return result;
                            event.setLength(0);
                        }
                        else if (line.startsWith("data:")) event.append(line.substring(5).trim()).append("\n");
                    }
                    addEvent(event.toString(), result);
                } catch (IOException error) {
                    if (result.isEmpty()) throw error;
                    org.telegram.messenger.FileLog.e("Liner stream interrupted; keeping completed providers", error);
                }
                return result;
            } finally { connection.disconnect(); }
        }
        return result;
    }
    private static boolean addEvent(String raw, ArrayList<Lyrics> out) {
        try {
            JSONObject event = new JSONObject(raw);
            if ("done".equals(event.optString("type"))) return true;
            if (!"provider".equals(event.optString("type")) || !"found".equals(event.optString("status"))) return false;
            JSONObject candidate = event.getJSONObject("candidate");
            JSONObject lyrics = candidate.getJSONObject("lyrics");
            Lyrics parsed = Lyrics.parse(lyrics.getString("content"), Lyrics.SOURCE_ONLINE, "Liner · " + candidate.optString("provider", event.optString("provider")));
            if (parsed != null) {
                for (int i = out.size() - 1; i >= 0; i--) {
                    if (parsed.provider.equals(out.get(i).provider)) out.remove(i);
                }
                out.add(parsed);
            } else {
                org.telegram.messenger.FileLog.e("Liner unsupported lyrics format: " + lyrics.optString("format")
                        + " provider=" + candidate.optString("provider", event.optString("provider")));
            }
        } catch (Exception error) {
            if (!raw.trim().isEmpty()) org.telegram.messenger.FileLog.e("Liner lyrics event could not be parsed", error);
        }
        return false;
    }
    private LinerApi() { }
}
