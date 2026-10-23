package app.exteraless.player;

import android.text.TextUtils;
import android.util.Base64;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public final class OnlineLyrics {

    public static final int OK = 0;
    public static final int NOT_FOUND = 1;
    public static final int ERROR = 2;
    public static final int PARTIAL = 3;

    public interface Callback {
        void onResult(ArrayList<Lyrics> sources, int status);
    }

    public static final class Query {
        public final String artist;
        public final String title;
        public final String originalTitle;
        public final String album;
        public final int duration;

        public Query(String artist, String title, String album, int duration) {
            String a = clean(artist);
            String t = clean(title);
            if (t != null) {
                t = AUDIO_EXT.matcher(t).replaceFirst("").replace('_', ' ').trim();
            }
            if (TextUtils.isEmpty(a) && t != null) {
                int dash = t.indexOf(" - ");
                if (dash > 0) {
                    a = t.substring(0, dash).trim();
                    t = t.substring(dash + 3).trim();
                }
            }
            this.artist = a;
            this.originalTitle = t;
            this.title = stripDecorations(t);
            this.album = clean(album);
            this.duration = duration;
        }

        public String key() {
            return (artist == null ? "" : artist.toLowerCase(Locale.ROOT)) + "\u0001" + (title == null ? "" : title.toLowerCase(Locale.ROOT)) + "\u0001" + duration + "\u0001liner:" + LinerApi.signedIn();
        }

        public boolean valid() {
            return !TextUtils.isEmpty(title);
        }
    }

    private static final class Found {
        final Lyrics lyrics;

        Found(Lyrics lyrics) {
            this.lyrics = lyrics;
        }
    }

    private static final String LRCLIB = "https://lrclib.net/api/";
    private static final String LRCMUX = "https://api.lrcmux.dev/get";
    private static final String NETEASE = "https://music.163.com/api/";
    private static final String QQ_SEARCH = "https://c.y.qq.com/soso/fcgi-bin/search_for_qq_cp";
    private static final String QQ_LYRIC = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg";
    private static final String KUGOU_SEARCH = "https://krcs.kugou.com/search";
    private static final String KUGOU_DOWNLOAD = "https://lyrics.kugou.com/download";
    private static final String LRCLIB_NAME = "LRCLIB";
    private static final String LRCMUX_NAME = "lrcmux";
    private static final String NETEASE_NAME = "NetEase";
    private static final String QQ_NAME = "QQ Music";
    private static final String KUGOU_NAME = "Kugou";
    private static final String BROWSER_UA = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36";
    private static final int CACHE_VERSION = 9;
    private static final int MAX_DURATION_DIFF = 3;
    private static final int MAX_FALLBACK_DURATION_DIFF = 5;
    private static final int ALIGN_WINDOW = 4;
    private static final float MIN_ALIGNED = 0.9f;
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern AUDIO_EXT = Pattern.compile("\\.(mp3|m4a|flac|ogg|oga|opus|wav|aac|alac|wma)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECORATION = Pattern.compile("\\s*[(\\[][^)\\]]*(official|lyric|video|audio|visualizer|remaster|hq|hd)[^)\\]]*[)\\]]", Pattern.CASE_INSENSITIVE);
    private static final DispatchQueue queue = new DispatchQueue("lyrics");
    private static final long CACHE_TTL_MS = 15 * 60 * 1000L;
    private static final LruCache<String, ArrayList<Lyrics>> memory = new LruCache<>(24);
    private static final LruCache<String, Long> memoryTime = new LruCache<>(24);

    private static ArrayList<Lyrics> freshMemory(String key) {
        Long saved = memoryTime.get(key);
        long age = saved == null ? CACHE_TTL_MS : android.os.SystemClock.elapsedRealtime() - saved;
        if (age < 0 || age >= CACHE_TTL_MS) {
            memory.remove(key); memoryTime.remove(key);
            return null;
        }
        return memory.get(key);
    }

    private static void rememberMemory(String key, ArrayList<Lyrics> sources) {
        memory.put(key, sources);
        memoryTime.put(key, android.os.SystemClock.elapsedRealtime());
    }
    private static final java.util.concurrent.ConcurrentHashMap<String, LyricsDeadline> activeBatches = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> refreshing = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final HashSet<String> missing = new HashSet<>();
    private static final ExecutorService pool = Executors.newFixedThreadPool(6);

    public static void clearSourceCache() {
        queue.postRunnable(() -> {
            File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "lrclib");
            File[] files = dir.listFiles();
            if (files != null) for (File file : files) if (file.isFile()) file.delete();
            AndroidUtilities.runOnUIThread(() -> { memory.evictAll(); memoryTime.evictAll(); missing.clear(); });
        });
    }

    private OnlineLyrics() {
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static String stripDecorations(String s) {
        if (s == null) {
            return null;
        }
        String out = DECORATION.matcher(s).replaceAll("").trim();
        return out.isEmpty() ? s : out;
    }

    public static ArrayList<Lyrics> cached(Query query) {
        return freshMemory(query.key());
    }

    public static boolean knownMissing(Query query) {
        return !LinerApi.signedIn() && missing.contains(query.key());
    }

    public static void loadCached(Query query, Callback callback) {
        String key = query.key();
        ArrayList<Lyrics> hit = freshMemory(key);
        if (hit != null) {
            callback.onResult(hit, OK);
            return;
        }
        queue.postRunnable(() -> {
            ArrayList<Lyrics> disk = readDisk(key);
            AndroidUtilities.runOnUIThread(() -> {
                if (disk != null) {
                    rememberMemory(key, disk);
                }
                callback.onResult(disk, disk != null ? OK : NOT_FOUND);
            });
        });
    }

    public static void refresh(Query query, Callback callback) {
        String key = query.key();
        if (!refreshing.add(key)) return;
        LyricsDeadline previous = activeBatches.get(key);
        if (previous != null) previous.cancelAsync();
        memory.remove(key);
        missing.remove(key);
        queue.postRunnable(() -> {
            File file = cacheFile(key);
            if (file != null && file.exists()) file.delete();
            AndroidUtilities.runOnUIThread(() -> fetchUncached(query, callback));
        });
    }

    private static void fetchUncached(Query query, Callback callback) {
        queue.postRunnable(() -> {
            FetchOutcome outcome = fetchAllSources(query, callback);
            if (outcome.sources != null && !outcome.networkFailed) writeDisk(query.key(), outcome.sources);
            AndroidUtilities.runOnUIThread(() -> {
                if (outcome.sources != null && !outcome.networkFailed) rememberMemory(query.key(), outcome.sources);
                refreshing.remove(query.key());
                callback.onResult(outcome.sources, outcome.sources != null ? (outcome.networkFailed ? PARTIAL : OK) : outcome.networkFailed ? ERROR : NOT_FOUND);
            });
        });
    }

    public static void fetch(Query query, Callback callback) {
        String key = query.key();
        ArrayList<Lyrics> hit = freshMemory(key);
        if (hit != null) {
            callback.onResult(hit, OK);
            return;
        }
        queue.postRunnable(() -> {
            ArrayList<Lyrics> result = readDisk(key);
            int status = OK;
            boolean cacheable = true;
            if (result == null) {
                FetchOutcome outcome = fetchAllSources(query, callback);
                result = outcome.sources;
                cacheable = !outcome.networkFailed;
                if (result != null && outcome.networkFailed) status = PARTIAL;
                if (result == null) {
                    status = outcome.networkFailed ? ERROR : NOT_FOUND;
                } else if (cacheable) {
                    writeDisk(key, result);
                }
            }
            final boolean saveMemory = cacheable;
            final ArrayList<Lyrics> sources = result;
            final int finalStatus = status;
            AndroidUtilities.runOnUIThread(() -> {
                if (sources != null) {
                    if (saveMemory) rememberMemory(key, sources);
                    missing.remove(key);
                } else if (finalStatus == NOT_FOUND) {
                    missing.add(key);
                }
                callback.onResult(sources, finalStatus);
            });
        });
    }

    private static final class FetchOutcome {
        final ArrayList<Lyrics> sources;
        final boolean networkFailed;

        FetchOutcome(ArrayList<Lyrics> sources, boolean networkFailed) {
            this.sources = sources;
            this.networkFailed = networkFailed;
        }
    }

    private static FetchOutcome fetchAllSources(Query query, Callback callback) {
        ArrayList<Lyrics> list = new ArrayList<>();
        AtomicInteger failures = new AtomicInteger(0);
        boolean linerEnabled = LinerApi.signedIn();
        CountDownLatch latch = new CountDownLatch(linerEnabled ? 6 : 5);
        LyricsDeadline deadline = new LyricsDeadline(60);
        String batchKey = query.key();
        activeBatches.put(batchKey, deadline);
        java.util.concurrent.atomic.AtomicBoolean linerFailed = new java.util.concurrent.atomic.AtomicBoolean(false);
        ArrayList<java.util.concurrent.Future<?>> tasks = new ArrayList<>();
        tasks.add(collectProvider(() -> requestLrclib(query), list, failures, latch, callback, deadline));
        tasks.add(collectProvider(() -> requestLrcmux(query), list, failures, latch, callback, deadline));
        tasks.add(collectProvider(() -> requestNetease(query), list, failures, latch, callback, deadline));
        tasks.add(collectProvider(() -> requestQq(query), list, failures, latch, callback, deadline));
        tasks.add(collectProvider(() -> requestKugou(query), list, failures, latch, callback, deadline));
        if (linerEnabled) tasks.add(collectProvider(() -> {
            try {
            LinerApi.lyrics(query, lyrics -> {
                ArrayList<Lyrics> snapshot;
                synchronized (list) {
                    if (!deadline.active) return;
                    list.removeIf(previous -> lyrics.provider.equals(previous.provider));
                    list.add(lyrics);
                    snapshot = new ArrayList<>(list);
                }
                sortSources(snapshot);
                AndroidUtilities.runOnUIThread(() -> { if (deadline.active) callback.onResult(snapshot, OK); });
            });
            return null;
            } catch (Exception error) { linerFailed.set(true); throw error; }
        }, list, failures, latch, callback, deadline));
        for (java.util.concurrent.Future<?> task : tasks) deadline.add(task);
        boolean completed = false;
        try {
            long until = android.os.SystemClock.elapsedRealtime() + 60000;
            while (deadline.active && latch.getCount() > 0 && android.os.SystemClock.elapsedRealtime() < until) {
                latch.await(200, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            completed = deadline.active && latch.getCount() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            deadline.close();
            activeBatches.remove(batchKey, deadline);
        }
        ArrayList<Lyrics> finished;
        synchronized (list) { finished = new ArrayList<>(list); }
        boolean failed = !completed || linerFailed.get();
        if (finished.isEmpty()) {
            return new FetchOutcome(null, failed || failures.get() >= (linerEnabled ? 6 : 5));
        }
        mergePunctuation(finished);
        sortSources(finished);
        return new FetchOutcome(finished, failed);
    }

    private static java.util.concurrent.Future<?> collectProvider(Callable<Found> provider, ArrayList<Lyrics> list, AtomicInteger failures, CountDownLatch latch, Callback callback, LyricsDeadline deadline) {
        return pool.submit(() -> {
            LyricsDeadline.CURRENT.set(deadline);
            try {
                if (!deadline.active) return;
                Found found = provider.call();
                if (found != null && found.lyrics != null) {
                    ArrayList<Lyrics> snapshot;
                    synchronized (list) {
                        if (!deadline.active) return;
                        list.add(found.lyrics);
                        snapshot = new ArrayList<>(list);
                    }
                    sortSources(snapshot);
                    AndroidUtilities.runOnUIThread(() -> { if (deadline.active) callback.onResult(snapshot, OK); });
                }
            } catch (Throwable e) {
                failures.incrementAndGet();
                FileLog.e(e);
            } finally {
                LyricsDeadline.CURRENT.remove();
                latch.countDown();
            }
        });
    }

    private static void mergePunctuation(ArrayList<Lyrics> list) {
        Lyrics timed = null;
        Lyrics plain = null;
        for (Lyrics l : list) {
            if (l.provider == null) {
                continue;
            }
            if (timed == null && l.lines.stream().allMatch(line -> line.words.isEmpty()) && l.synced && !l.instrumental && l.provider.startsWith(LRCMUX_NAME)) {
                timed = l;
            }
            if (plain == null && !l.synced && !l.instrumental && l.provider.startsWith(LRCLIB_NAME)) {
                plain = l;
            }
        }
        if (timed != null && plain != null) {
            Lyrics punctuated = punctuate(timed, plain);
            if (punctuated != null) {
                int index = list.indexOf(timed);
                if (index >= 0) {
                    list.set(index, punctuated);
                }
            }
        }
    }

    private static void sortSources(ArrayList<Lyrics> list) {
        Collections.sort(list, (a, b) -> {
            if (a.providerTopPriority != b.providerTopPriority) return a.providerTopPriority ? -1 : 1;
            int rankA = a.lines.stream().anyMatch(line -> !line.words.isEmpty()) ? 3 : a.instrumental ? 1 : (a.synced ? 2 : 0);
            int rankB = b.lines.stream().anyMatch(line -> !line.words.isEmpty()) ? 3 : b.instrumental ? 1 : (b.synced ? 2 : 0);
            if (rankA != rankB) {
                return rankB - rankA;
            }
            if (a.providerSyncRank >= 0 && b.providerSyncRank >= 0) {
                if (a.providerSyncRank != b.providerSyncRank) return b.providerSyncRank - a.providerSyncRank;
                int quality = Double.compare(b.providerQuality, a.providerQuality);
                if (quality != 0) return quality;
                String pa = a.provider.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
                String pb = b.provider.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
                boolean preferredA = pa.endsWith("polaris mono") || pa.endsWith("binimum");
                boolean preferredB = pb.endsWith("polaris mono") || pb.endsWith("binimum");
                if (preferredA != preferredB) return preferredA ? -1 : 1;
            }
            return providerPriority(a) - providerPriority(b);
        });
    }

    private static int providerPriority(Lyrics l) {
        String p = l.provider == null ? "" : l.provider;
        if (p.startsWith(LRCLIB_NAME)) return 0;
        if (p.startsWith(LRCMUX_NAME)) return 1;
        if (p.startsWith(NETEASE_NAME)) return 2;
        if (p.startsWith(QQ_NAME)) return 3;
        if (p.startsWith(KUGOU_NAME)) return 4;
        return 9;
    }

    private static Found requestLrclib(Query q) throws Exception {
        if (!TextUtils.isEmpty(q.artist)) {
            StringBuilder url = new StringBuilder(LRCLIB).append("get?artist_name=").append(enc(q.artist))
                    .append("&track_name=").append(enc(q.title));
            if (!TextUtils.isEmpty(q.album)) {
                url.append("&album_name=").append(enc(q.album));
            }
            if (q.duration > 0) {
                url.append("&duration=").append(q.duration);
            }
            String body = get(url.toString());
            if (body != null) {
                Found found = fromLrclib(new JSONObject(body));
                if (found != null) {
                    return found;
                }
            }
        }
        StringBuilder url = new StringBuilder(LRCLIB).append("search?track_name=").append(enc(q.title));
        if (!TextUtils.isEmpty(q.artist)) {
            url.append("&artist_name=").append(enc(q.artist));
        }
        String body = get(url.toString());
        if (body == null) {
            return null;
        }
        JSONArray arr = new JSONArray(body);
        Found best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            Found found = o == null ? null : fromLrclib(o);
            if (found == null) {
                continue;
            }
            int score = found.lyrics.synced ? 40 : 0;
            if (q.duration > 0 && o.has("duration")) {
                int diff = (int) Math.abs(Math.round(o.optDouble("duration", 0)) - q.duration);
                score -= diff <= MAX_DURATION_DIFF ? diff : 20 + Math.min(diff, 60);
            }
            if (score > bestScore) {
                bestScore = score;
                best = found;
            }
        }
        return best;
    }

    private static String optString(JSONObject o, String name) {
        if (o == null || o.isNull(name)) {
            return null;
        }
        String s = o.optString(name, null);
        return TextUtils.isEmpty(s) ? null : s;
    }

    private static Found fromLrclib(JSONObject o) {
        if (o.optBoolean("instrumental", false)) {
            return new Found(Lyrics.instrumental(Lyrics.SOURCE_ONLINE, LRCLIB_NAME));
        }
        Lyrics synced = Lyrics.parse(optString(o, "syncedLyrics"), Lyrics.SOURCE_ONLINE, LRCLIB_NAME);
        if (synced != null && synced.synced) {
            return new Found(synced);
        }
        Lyrics plain = Lyrics.parse(optString(o, "plainLyrics"), Lyrics.SOURCE_ONLINE, LRCLIB_NAME);
        if (plain != null) {
            return new Found(plain);
        }
        return synced != null ? new Found(synced) : null;
    }

    private static Found requestLrcmux(Query q) throws Exception {
        StringBuilder url = new StringBuilder(LRCMUX).append("?title=").append(enc(q.title)).append("&level=line&format=json");
        if (!TextUtils.isEmpty(q.artist)) {
            url.append("&artist=").append(enc(q.artist));
        }
        if (!TextUtils.isEmpty(q.album)) {
            url.append("&album=").append(enc(q.album));
        }
        if (q.duration > 0) {
            url.append("&duration=").append(q.duration);
        }
        String body = get(url.toString());
        if (body == null) {
            return null;
        }
        JSONObject o = new JSONObject(body);
        JSONObject track = o.optJSONObject("track");
        JSONObject meta = o.optJSONObject("meta");
        if (q.duration > 0 && track != null) {
            int duration = track.optInt("duration", 0);
            if (duration > 0 && Math.abs(duration - q.duration) > MAX_FALLBACK_DURATION_DIFF) {
                return null;
            }
        }
        JSONObject source = meta != null ? meta.optJSONObject("source") : null;
        String sourceName = source != null ? optString(source, "name") : null;
        String provider = sourceName != null ? "lrcmux · " + sourceName : "lrcmux";
        if (meta != null && meta.optBoolean("instrumental", false)) {
            return new Found(Lyrics.instrumental(Lyrics.SOURCE_ONLINE, provider));
        }
        JSONArray lines = o.optJSONArray("lines");
        if (lines == null || lines.length() == 0) {
            return null;
        }
        boolean synced = meta != null && !"none".equals(meta.optString("level"));
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < lines.length(); i++) {
            JSONObject line = lines.optJSONObject(i);
            if (line == null) {
                continue;
            }
            if (synced) {
                long start = Math.max(0, line.optLong("start", 0));
                text.append(String.format(Locale.US, "[%02d:%02d.%02d]", start / 60000, start / 1000 % 60, start % 1000 / 10));
            }
            text.append(line.optString("text", "")).append('\n');
        }
        Lyrics lyrics = Lyrics.parse(text.toString(), Lyrics.SOURCE_ONLINE, provider);
        return lyrics == null ? null : new Found(lyrics);
    }

    private static Found requestNetease(Query q) throws Exception {
        if (TextUtils.isEmpty(q.title)) {
            return null;
        }
        String keyword = (TextUtils.isEmpty(q.artist) ? "" : q.artist + " ") + q.title;
        StringBuilder url = new StringBuilder(NETEASE).append("search/get?s=").append(enc(keyword))
                .append("&type=1&limit=12&offset=0");
        JSONObject o = new JSONObject(get(url.toString(), "https://music.163.com/", BROWSER_UA));
        JSONObject result = o.optJSONObject("result");
        JSONArray songs = result == null ? null : result.optJSONArray("songs");
        if (songs == null || songs.length() == 0) {
            return null;
        }
        JSONObject best = null;
        int bestDiff = Integer.MAX_VALUE;
        String titleLower = q.title.toLowerCase(Locale.ROOT);
        for (int i = 0; i < songs.length(); i++) {
            JSONObject s = songs.optJSONObject(i);
            if (s == null) {
                continue;
            }
            String name = optString(s, "name");
            if (name == null || !name.toLowerCase(Locale.ROOT).contains(titleLower)) {
                continue;
            }
            int duration = (int) Math.round(s.optLong("duration", 0) / 1000.0);
            int diff = q.duration > 0 && duration > 0 ? Math.abs(duration - q.duration) : 0;
            if (diff < bestDiff) {
                bestDiff = diff;
                best = s;
            }
        }
        if (best == null) {
            return null;
        }
        long id = best.optLong("id", 0);
        if (id == 0) {
            return null;
        }
        int duration = (int) Math.round(best.optLong("duration", 0) / 1000.0);
        if (q.duration > 0 && duration > 0 && Math.abs(duration - q.duration) > MAX_FALLBACK_DURATION_DIFF) {
            return null;
        }
        JSONObject lo = new JSONObject(get(NETEASE + "song/lyric?id=" + id + "&lv=1&kv=1&tv=-1", "https://music.163.com/", BROWSER_UA));
        JSONObject lrc = lo.optJSONObject("lrc");
        String text = lrc == null ? null : optString(lrc, "lyric");
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        Lyrics lyrics = Lyrics.parse(text, Lyrics.SOURCE_ONLINE, NETEASE_NAME);
        return lyrics != null ? new Found(lyrics) : null;
    }

    private static Found requestQq(Query q) throws Exception {
        if (TextUtils.isEmpty(q.title)) {
            return null;
        }
        String keyword = (TextUtils.isEmpty(q.artist) ? "" : q.artist + " ") + q.title;
        StringBuilder url = new StringBuilder(QQ_SEARCH).append("?w=").append(enc(keyword))
                .append("&format=json&n=12&p=1&g_tk=5381");
        JSONObject o = new JSONObject(get(url.toString(), "https://y.qq.com/", BROWSER_UA));
        JSONObject data = o.optJSONObject("data");
        JSONObject song = data == null ? null : data.optJSONObject("song");
        JSONArray list = song == null ? null : song.optJSONArray("list");
        if (list == null || list.length() == 0) {
            return null;
        }
        JSONObject best = null;
        int bestDiff = Integer.MAX_VALUE;
        String titleLower = q.title.toLowerCase(Locale.ROOT);
        for (int i = 0; i < list.length(); i++) {
            JSONObject s = list.optJSONObject(i);
            if (s == null) {
                continue;
            }
            String name = optString(s, "songname");
            if (name == null || !name.toLowerCase(Locale.ROOT).contains(titleLower)) {
                continue;
            }
            int duration = s.optInt("interval", 0);
            int diff = q.duration > 0 && duration > 0 ? Math.abs(duration - q.duration) : 0;
            if (diff < bestDiff) {
                bestDiff = diff;
                best = s;
            }
        }
        if (best == null) {
            return null;
        }
        int duration = best.optInt("interval", 0);
        if (q.duration > 0 && duration > 0 && Math.abs(duration - q.duration) > MAX_FALLBACK_DURATION_DIFF) {
            return null;
        }
        String mid = optString(best, "songmid");
        if (mid == null) {
            return null;
        }
        StringBuilder lyricUrl = new StringBuilder(QQ_LYRIC).append("?songmid=").append(enc(mid))
                .append("&format=json&nobase64=1&g_tk=5381");
        JSONObject lo = new JSONObject(get(lyricUrl.toString(), "https://y.qq.com/portal/player.html", BROWSER_UA));
        String text = optString(lo, "lyric");
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        Lyrics lyrics = Lyrics.parse(text, Lyrics.SOURCE_ONLINE, QQ_NAME);
        return lyrics != null ? new Found(lyrics) : null;
    }

    private static Found requestKugou(Query q) throws Exception {
        if (TextUtils.isEmpty(q.title)) {
            return null;
        }
        String keyword = (TextUtils.isEmpty(q.artist) ? "" : q.artist + " ") + q.title;
        StringBuilder url = new StringBuilder(KUGOU_SEARCH).append("?ver=1&man=yes&client=mobi&keyword=").append(enc(keyword));
        JSONObject o = new JSONObject(get(url.toString(), null, BROWSER_UA));
        JSONArray candidates = o.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            return null;
        }
        JSONObject best = null;
        int bestDiff = Integer.MAX_VALUE;
        String titleLower = q.title.toLowerCase(Locale.ROOT);
        for (int i = 0; i < candidates.length(); i++) {
            JSONObject c = candidates.optJSONObject(i);
            if (c == null) {
                continue;
            }
            String name = optString(c, "song");
            if (name == null || !name.toLowerCase(Locale.ROOT).contains(titleLower)) {
                continue;
            }
            long raw = c.optLong("duration", 0);
            int duration = raw > 10000 ? (int) Math.round(raw / 1000.0) : (int) raw;
            int diff = q.duration > 0 && duration > 0 ? Math.abs(duration - q.duration) : 0;
            if (diff < bestDiff) {
                bestDiff = diff;
                best = c;
            }
        }
        if (best == null) {
            return null;
        }
        long raw = best.optLong("duration", 0);
        int duration = raw > 10000 ? (int) Math.round(raw / 1000.0) : (int) raw;
        if (q.duration > 0 && duration > 0 && Math.abs(duration - q.duration) > MAX_FALLBACK_DURATION_DIFF) {
            return null;
        }
        Object id = best.opt("id");
        Object accesskey = best.opt("accesskey");
        if (id == null || accesskey == null) {
            return null;
        }
        StringBuilder download = new StringBuilder(KUGOU_DOWNLOAD).append("?ver=1&client=pc&id=").append(id)
                .append("&accesskey=").append(enc(String.valueOf(accesskey))).append("&fmt=lrc&charset=utf8");
        JSONObject d = new JSONObject(get(download.toString(), null, BROWSER_UA));
        String content = optString(d, "content");
        if (TextUtils.isEmpty(content)) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.decode(content, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.trim().isEmpty()) {
            return null;
        }
        Lyrics lyrics = Lyrics.parse(text, Lyrics.SOURCE_ONLINE, KUGOU_NAME);
        return lyrics != null ? new Found(lyrics) : null;
    }

    private static String wordKey(String word) {
        return NOT_WORD.matcher(word.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private static int firstLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String withCase(String token, String original) {
        int t = firstLetter(token);
        int o = firstLetter(original);
        if (t < 0 || o < 0) {
            return token;
        }
        boolean upper = Character.isUpperCase(original.charAt(o));
        char c = token.charAt(t);
        if (Character.isUpperCase(c) == upper) {
            return token;
        }
        return token.substring(0, t) + (upper ? Character.toUpperCase(c) : Character.toLowerCase(c)) + token.substring(t + 1);
    }

    private static void appendWord(StringBuilder sb, String word) {
        if (word.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(word);
    }

    private static Lyrics punctuate(Lyrics timed, Lyrics plain) {
        ArrayList<String> tokens = new ArrayList<>();
        ArrayList<String> keys = new ArrayList<>();
        for (Lyrics.Line line : plain.lines) {
            for (String word : line.text.split("\\s+")) {
                String key = wordKey(word);
                if (!key.isEmpty()) {
                    tokens.add(word);
                    keys.add(key);
                }
            }
        }
        if (keys.isEmpty()) {
            return null;
        }
        int pos = 0;
        int total = 0;
        int matched = 0;
        ArrayList<Lyrics.Line> out = new ArrayList<>(timed.lines.size());
        for (Lyrics.Line line : timed.lines) {
            StringBuilder sb = new StringBuilder();
            for (String word : line.text.split("\\s+")) {
                String key = wordKey(word);
                if (key.isEmpty()) {
                    appendWord(sb, word);
                    continue;
                }
                total++;
                int hit = -1;
                for (int j = pos, end = Math.min(pos + ALIGN_WINDOW, keys.size()); j < end; j++) {
                    if (keys.get(j).equals(key)) {
                        hit = j;
                        break;
                    }
                }
                if (hit < 0) {
                    appendWord(sb, word);
                    continue;
                }
                matched++;
                appendWord(sb, withCase(tokens.get(hit), word));
                pos = hit + 1;
            }
            out.add(new Lyrics.Line(line.time, sb.toString()));
        }
        if (total == 0 || matched < total * MIN_ALIGNED) {
            return null;
        }
        return Lyrics.synced(out, timed.source, timed.provider + " + " + LRCLIB_NAME);
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }

    private static String get(String url) throws Exception {
        return get(url, null, null);
    }

    private static String get(String url, String referer, String userAgent) throws Exception {
        HttpURLConnection connection = LyricsDeadline.track((HttpURLConnection) new URL(url).openConnection());
        try {
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setRequestProperty("User-Agent", userAgent != null ? userAgent : "exteraless/" + BuildVars.BUILD_VERSION_STRING + " (https://github.com/exteraless/exteraless)");
            connection.setRequestProperty("Accept", "application/json");
            if (referer != null) {
                connection.setRequestProperty("Referer", referer);
            }
            int code = connection.getResponseCode();
            if (code == 404) {
                return null;
            }
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("lyrics http " + code + " " + url);
            }
            try (InputStream in = connection.getInputStream()) {
                return readAll(in);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toString("UTF-8");
    }

    private static File cacheFile(String key) {
        File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "lrclib");
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        return new File(dir, Utilities.MD5(key) + ".json");
    }

    private static ArrayList<Lyrics> readDisk(String key) {
        try {
            File f = cacheFile(key);
            if (f == null || !f.exists()) {
                return null;
            }
            JSONObject o;
            try (FileInputStream in = new FileInputStream(f)) {
                o = new JSONObject(readAll(in));
            }
            long age = System.currentTimeMillis() - o.optLong("savedAt", 0);
            if (age < 0 || age >= CACHE_TTL_MS) return null;
            int version = o.optInt("v", 1);
            if (version < CACHE_VERSION) {
                // pre-multi-source caches hold a single result and would hide other providers
                return null;
            }
            JSONArray arr = o.optJSONArray("sources");
            if (arr == null || arr.length() == 0) {
                return null;
            }
            ArrayList<Lyrics> out = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.optJSONObject(i);
                if (e == null) {
                    continue;
                }
                String provider = optString(e, "provider");
                if (provider == null) {
                    provider = LRCLIB_NAME;
                }
                if (e.optBoolean("instrumental", false)) {
                    out.add(Lyrics.instrumental(Lyrics.SOURCE_ONLINE, provider));
                    continue;
                }
                Lyrics lyrics = Lyrics.parse(optString(e, "text"), Lyrics.SOURCE_ONLINE, provider);
                if (lyrics != null) {
                    lyrics.providerTopPriority = e.optBoolean("topPriority");
                    lyrics.providerQuality = e.optDouble("quality", 0);
                    lyrics.providerSyncRank = e.optInt("syncRank", -1);
                    out.add(lyrics);
                }
            }
            return out.isEmpty() ? null : out;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void writeDisk(String key, ArrayList<Lyrics> sources) {
        try {
            File f = cacheFile(key);
            if (f == null) {
                return;
            }
            JSONObject o = new JSONObject();
            o.put("v", CACHE_VERSION);
            o.put("savedAt", System.currentTimeMillis());
            JSONArray arr = new JSONArray();
            for (int i = 0; i < sources.size(); i++) {
                Lyrics lyrics = sources.get(i);
                JSONObject e = new JSONObject();
                e.put("provider", lyrics.provider);
                e.put("instrumental", lyrics.instrumental);
                e.put("topPriority", lyrics.providerTopPriority);
                e.put("quality", lyrics.providerQuality);
                e.put("syncRank", lyrics.providerSyncRank);
                e.put("text", lyrics.toText());
                arr.put(e);
            }
            o.put("sources", arr);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
