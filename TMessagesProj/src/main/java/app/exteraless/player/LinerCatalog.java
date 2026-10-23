package app.exteraless.player;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;

/** YouTube Music catalog contract used by Liner's client-side search. */
final class LinerCatalog {
    static final class Track {
        final String id, title, artist, hintArtist;
        final long durationMs;
        Track(String id, String title, String artist, String hintArtist, long durationMs) {
            this.id = id; this.title = title; this.artist = artist;
            this.hintArtist = hintArtist; this.durationMs = durationMs;
        }
    }

    static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    static Track resolve(OnlineLyrics.Query query) {
        ArrayList<Track> tracks = new ArrayList<>();
        // Songs first, then unfiltered results, as in Liner's client catalog.
        for (String filter : new String[]{"EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D", ""}) {
            try { collect(search(query.artist + " " + query.originalTitle, filter), tracks, 0); }
            catch (Exception error) { android.util.Log.w("LinkiLyrics", "Liner client catalog search failed: " + error.getClass().getSimpleName()); }
            Track matched = match(tracks, query);
            if (matched != null) return matched;
        }
        return null;
    }

    static Track match(ArrayList<Track> tracks, OnlineLyrics.Query query) {
        String title = normalize(query.title), original = normalize(query.originalTitle), artist = normalize(query.artist);
        if (title.isEmpty() || artist.isEmpty()) return null;
        Track best = null;
        long difference = Long.MAX_VALUE;
        for (Track track : tracks) {
            String candidateTitle = normalize(track.title), candidateArtist = normalize(track.artist);
            if (!title.equals(candidateTitle) && !original.equals(candidateTitle)) continue;
            if (!(" " + candidateArtist + " ").contains(" " + artist + " ")
                    && !(" " + artist + " ").contains(" " + candidateArtist + " ")) continue;
            if (candidateArtist.isEmpty()) continue;
            long delta = track.durationMs > 0 && query.duration > 0 ? Math.abs(track.durationMs - query.duration * 1000L) : 0;
            if (delta > 8000 || delta >= difference) continue;
            best = track; difference = delta;
        }
        return best;
    }

    private static String visitor = "";
    private static long visitorExpires;

    private static synchronized String visitorData() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now < visitorExpires) return visitor;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("https://www.youtube.com/sw.js_data").openConnection();
            connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = in.read(buffer)) != -1) {
                    if (out.size() + n > 2 * 1024 * 1024) throw new java.io.IOException("Visitor response limit");
                    out.write(buffer, 0, n);
                }
                java.util.regex.Matcher match = java.util.regex.Pattern.compile("Cg[s-v][A-Za-z0-9_-]{20,}(?:%3D|=){0,2}").matcher(out.toString("UTF-8"));
                if (match.find()) {
                    visitor = match.group();
                    visitorExpires = now + 330 * 60 * 1000L;
                    return visitor;
                }
            }
        } catch (Exception ignored) {
            // Search can still work without visitorData, as in the desktop client.
        } finally { if (connection != null) connection.disconnect(); }
        visitorExpires = now + 60000;
        return visitor;
    }

    private static JSONObject search(String query, String filter) throws Exception {
        String version = "1.20260911.08.00";
        JSONObject client = new JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", version).put("hl", "en").put("gl", "US");
        String visitor = visitorData();
        if (!visitor.isEmpty()) client.put("visitorData", visitor);
        JSONObject body = new JSONObject().put("query", query).put("context", new JSONObject()
                .put("client", client).put("user", new JSONObject().put("lockedSafetyMode", false)));
        if (!filter.isEmpty()) body.put("params", filter);
        HttpURLConnection connection = (HttpURLConnection) new URL("https://music.youtube.com/youtubei/v1/search?prettyPrint=false").openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(10000);
        connection.setRequestMethod("POST"); connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("x-youtube-client-name", "67");
        if (!visitor.isEmpty()) connection.setRequestProperty("x-goog-visitor-id", visitor);
        connection.setRequestProperty("x-youtube-client-version", version);
        connection.setRequestProperty("Origin", "https://music.youtube.com");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        try {
            try (java.io.OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            if (connection.getResponseCode() != 200) throw new java.io.IOException("Catalog HTTP " + connection.getResponseCode());
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = in.read(buffer)) != -1) {
                    if (out.size() + n > 4 * 1024 * 1024) throw new java.io.IOException("Catalog response limit");
                    out.write(buffer, 0, n);
                }
                return new JSONObject(out.toString("UTF-8"));
            }
        } finally { connection.disconnect(); }
    }

    private static JSONObject object(JSONObject value, String key) {
        JSONObject result = value == null ? null : value.optJSONObject(key);
        return result == null ? new JSONObject() : result;
    }

    private static void collect(Object value, ArrayList<Track> tracks, int depth) {
        if (depth > 40) return;
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) collect(array.opt(i), tracks, depth + 1);
        } else if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            JSONObject renderer = obj.optJSONObject("musicResponsiveListItemRenderer");
            if (renderer != null) {
                Track track = parseTrack(renderer);
                if (track != null) tracks.add(track);
            }
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) collect(obj.opt(keys.next()), tracks, depth + 1);
        }
    }

    private static Track parseTrack(JSONObject renderer) {
        String id = object(renderer, "playlistItemData").optString("videoId");
        String title = "", firstArtist = ""; StringBuilder artists = new StringBuilder(); long duration = 0;
        JSONArray columns = renderer.optJSONArray("flexColumns");
        if (columns == null) return null;
        JSONArray fixed = renderer.optJSONArray("fixedColumns");
        for (int column = 0; column < columns.length() + (fixed == null ? 0 : fixed.length()); column++) {
            boolean isFixed = column >= columns.length();
            JSONObject entry = isFixed ? fixed.optJSONObject(column - columns.length()) : columns.optJSONObject(column);
            JSONObject content = object(entry, isFixed ? "musicResponsiveListItemFixedColumnRenderer" : "musicResponsiveListItemFlexColumnRenderer");
            JSONArray runs = object(content, "text").optJSONArray("runs");
            if (runs == null) continue;
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < runs.length(); i++) {
                JSONObject run = runs.optJSONObject(i); if (run == null) continue;
                String part = run.optString("text"); text.append(part);
                JSONObject nav = object(run, "navigationEndpoint");
                if (id.isEmpty()) id = object(nav, "watchEndpoint").optString("videoId");
                JSONObject browse = object(nav, "browseEndpoint");
                String pageType = object(object(browse, "browseEndpointContextSupportedConfigs"), "browseEndpointContextMusicConfig").optString("pageType");
                if (pageType.equals("MUSIC_PAGE_TYPE_ARTIST") || pageType.equals("MUSIC_PAGE_TYPE_USER_CHANNEL")) {
                    if (firstArtist.isEmpty()) firstArtist = part;
                    if (artists.length() > 0) artists.append(' ');
                    artists.append(part);
                }
                if (part.trim().matches("\\d{1,3}:\\d{2}(:\\d{2})?")) {
                    long seconds = 0;
                    for (String piece : part.trim().split(":")) seconds = seconds * 60 + Long.parseLong(piece);
                    duration = seconds * 1000;
                }
            }
            if (column == 0) title = text.toString();
        }
        if (id.isEmpty()) id = object(object(renderer, "navigationEndpoint"), "watchEndpoint").optString("videoId");
        return id.isEmpty() || title.isEmpty() || artists.length() == 0 ? null : new Track(id, title, artists.toString(), firstArtist, duration);
    }
}
