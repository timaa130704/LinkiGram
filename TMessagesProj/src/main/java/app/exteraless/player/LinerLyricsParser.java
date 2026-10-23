package app.exteraless.player;

import android.os.Looper;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Offline @braccato/parsers runtime, matching Liner's parser version and formats. */
final class LinerLyricsParser {
    // UI-thread owned; never loads a remote page, exposes Java objects, or executes lyric text.
    private static WebView engine;
    private static boolean ready;
    private static final ArrayList<Request> pending = new ArrayList<>();
    private static volatile String script;

    private static final class Request {
        final String expression;
        final CountDownLatch done = new CountDownLatch(1);
        volatile String result;
        volatile boolean cancelled;
        Request(String expression) { this.expression = expression; }
    }

    static Lyrics parse(String raw, String format, String provider, long durationMs) {
        if (raw == null || raw.length() > 4 * 1024 * 1024) return null;
        if (Looper.myLooper() == Looper.getMainLooper()) return Lyrics.parse(raw, Lyrics.SOURCE_ONLINE, provider);
        try {
            if (script == null) {
                try (InputStream in = ApplicationLoader.applicationContext.getAssets().open("liner-lyrics/engine.js");
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                    script = out.toString("UTF-8");
                }
            }
            Request request = new Request("JSON.stringify(LinkiLinerLyrics.parse(" + JSONObject.quote(raw) + ","
                    + JSONObject.quote(format) + "," + Math.max(0, durationMs) + "))");
            AndroidUtilities.runOnUIThread(() -> submit(request));
            if (!request.done.await(20, TimeUnit.SECONDS)) {
                request.cancelled = true;
                AndroidUtilities.runOnUIThread(() -> pending.remove(request));
                throw new java.io.IOException("Liner parser timeout");
            }
            if (request.result == null) throw new java.io.IOException("Liner parser unavailable");
            Object decoded = new JSONTokener(request.result).nextValue();
            if (!(decoded instanceof String)) throw new java.io.IOException("Invalid Liner parser response");
            JSONObject parsed = new JSONObject((String) decoded);
            JSONArray rows = parsed.getJSONArray("lines");
            if (!parsed.optBoolean("synced")) {
                StringBuilder plain = new StringBuilder();
                for (int i = 0; i < rows.length(); i++) {
                    if (i > 0) plain.append('\n');
                    plain.append(rows.getJSONObject(i).optString("text"));
                }
                return Lyrics.parse(plain.toString(), Lyrics.SOURCE_ONLINE, provider);
            }
            ArrayList<Lyrics.Line> lines = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String text = row.optString("text");
                ArrayList<Lyrics.Word> words = new ArrayList<>();
                JSONArray parts = row.optJSONArray("words");
                int cursor = 0;
                if (parts != null) for (int j = 0; j < parts.length(); j++) {
                    JSONObject part = parts.getJSONObject(j);
                    int start = part.optInt("start", -1), end = part.optInt("end", -1);
                    if (start < cursor || end <= start || end > text.length()) continue;
                    words.add(new Lyrics.Word(Math.max(0, part.optLong("time")), start, end));
                    cursor = end;
                }
                lines.add(new Lyrics.Line(Math.max(0, row.optLong("time")), text, words));
            }
            lines.sort((a, b) -> Long.compare(a.time, b.time));
            return lines.isEmpty() ? null : Lyrics.synced(lines, Lyrics.SOURCE_ONLINE, provider);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception error) {
            android.util.Log.w("LinkiLyrics", "Liner parser fallback: " + error.getClass().getSimpleName());
            return Lyrics.parse(raw, Lyrics.SOURCE_ONLINE, provider);
        }
    }

    private static void submit(Request request) {
        if (request.cancelled) return;
        if (ready && engine != null) { evaluate(request); return; }
        pending.add(request);
        if (engine != null) return;
        try {
            engine = new WebView(ApplicationLoader.applicationContext);
            engine.getSettings().setJavaScriptEnabled(true);
            engine.getSettings().setAllowFileAccess(false);
            engine.getSettings().setAllowContentAccess(false);
            engine.getSettings().setBlockNetworkLoads(true);
            engine.setWebViewClient(new WebViewClient() {
                private boolean initialized;
                @Override public void onPageFinished(WebView view, String url) {
                    if (initialized) return;
                    initialized = true;
                    view.evaluateJavascript(script + "\n;typeof LinkiLinerLyrics.parse === 'function'", result -> {
                        if (!"true".equals(result)) { failPending(); return; }
                        ready = true;
                        ArrayList<Request> queued = new ArrayList<>(pending);
                        pending.clear();
                        for (Request next : queued) evaluate(next);
                    });
                }
            });
            engine.loadDataWithBaseURL(null, "<!doctype html><html><head><meta charset='utf-8'></head><body></body></html>", "text/html", "UTF-8", null);
        } catch (Throwable error) { failPending(); }
    }

    private static void evaluate(Request request) {
        if (request.cancelled) return;
        try {
            engine.evaluateJavascript(request.expression, result -> {
                if (!request.cancelled) request.result = result;
                request.done.countDown();
            });
        } catch (Throwable error) { request.done.countDown(); }
    }

    private static void failPending() {
        ready = false;
        for (Request request : pending) request.done.countDown();
        pending.clear();
        if (engine != null) { engine.destroy(); engine = null; }
    }
}
