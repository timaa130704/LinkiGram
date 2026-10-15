package app.nimarkogram.messenger.xcfg;

/*
 * HTTP-клиент X-CFG: HttpURLConnection, без внешних зависимостей.
 * Порт логики requests-вызовов плагина X-CFG (автор @x_vare, GPL).
 */
import android.os.Build;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class XcfgApi {

    public interface ProgressCallback {
        void onProgress(int percent);
    }

    public static class ApiResp {
        public final int code;
        public final JSONObject json;

        ApiResp(int code, JSONObject json) {
            this.code = code;
            this.json = json;
        }
    }

    public static class Sub {
        public final List<String> links;
        public final String stub;

        Sub(List<String> links, String stub) {
            this.links = links;
            this.stub = stub;
        }
    }

    private XcfgApi() {
    }

    private static HttpURLConnection open(String url, String method, Map<String, String> headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", XcfgPrefs.UA);
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getValue() != null) {
                    c.setRequestProperty(e.getKey(), e.getValue());
                }
            }
        }
        return c;
    }

    private static String readStream(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static JSONObject parseJson(String body) {
        try {
            return new JSONObject(body);
        } catch (Exception e) {
            JSONObject o = new JSONObject();
            try {
                o.put("message", body == null ? "" : body.trim());
            } catch (Exception ignore) {
            }
            return o;
        }
    }

    public static ApiResp post(String path, JSONObject body, String token) throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        }
        HttpURLConnection c = open(XcfgPrefs.API + path, "POST", headers);
        c.setDoOutput(true);
        byte[] data = (body == null ? new JSONObject() : body).toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(data.length);
        try (java.io.OutputStream os = c.getOutputStream()) {
            os.write(data);
        }
        int code = c.getResponseCode();
        String resp = readStream(code >= 400 ? c.getErrorStream() : c.getInputStream());
        c.disconnect();
        return new ApiResp(code, parseJson(resp));
    }

    public static ApiResp pluginLogin(String code) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("code", code);
        } catch (Exception ignore) {
        }
        return post("/api/app/plugin_login", body, null);
    }

    public static String get(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection c = open(url, "GET", headers);
        int code = c.getResponseCode();
        String resp = readStream(code >= 400 ? c.getErrorStream() : c.getInputStream());
        c.disconnect();
        if (code >= 400) {
            throw new IOException("HTTP " + code);
        }
        return resp;
    }

    /** Серверы из подписки: плагин — отдельное устройство подписки («X-CFG · Telegram»). */
    public static Sub fetchLinks(String subUrl, String hwid) throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-HWID", hwid);
        headers.put("X-Device-OS", "Android");
        headers.put("X-Ver-OS", Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE);
        headers.put("X-Device-Model", "Telegram · " + Build.MANUFACTURER + " " + Build.MODEL);
        String body = get(subUrl, headers).trim();
        if (!body.contains("vless://")) {
            try {
                byte[] pad = new byte[]{'='};
                int need = (-body.length()) % 4;
                StringBuilder padded = new StringBuilder(body);
                for (int i = 0; i < need; i++) {
                    padded.append('=');
                }
                body = new String(Base64.decode(padded.toString(), Base64.DEFAULT), StandardCharsets.UTF_8);
            } catch (Exception ignore) {
            }
        }
        List<String> links = new ArrayList<>();
        String stub = "";
        for (String rawLine : body.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (!line.toLowerCase(Locale.ROOT).startsWith("vless://")) {
                continue;
            }
            XcfgVless.Parsed p = XcfgVless.parse(line);
            if (p == null) {
                continue;
            }
            if ("127.0.0.1".equals(p.address) || "0.0.0.0".equals(p.address) || p.port == 0 || p.port == 1) {
                // заглушка сайта: лимит устройств, истекла и т.п.
                stub = p.remark == null || p.remark.isEmpty() ? "подписка недоступна" : p.remark;
                continue;
            }
            links.add(line);
        }
        return new Sub(links, stub);
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** Скачивает файл в out с проверкой SHA-256 (хеш скачиваемого содержимого). */
    public static void downloadChecked(String url, File out, String expectedSha256, ProgressCallback cb) throws Exception {
        HttpURLConnection c = open(url, "GET", null);
        // 45 с достаточно чтобы понять, что сервер перестал отдавать данные:
        // при обрыве стрима дальше сработает Range-fallback в XcfgCore.
        c.setReadTimeout(45000);
        int code = c.getResponseCode();
        if (code >= 400) {
            c.disconnect();
            throw new IOException("HTTP " + code + " for " + url);
        }
        int total = c.getContentLength();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = c.getInputStream(); FileOutputStream fo = new FileOutputStream(out)) {
            byte[] buf = new byte[256 * 1024];
            int got = 0, n;
            while ((n = in.read(buf)) > 0) {
                fo.write(buf, 0, n);
                md.update(buf, 0, n);
                got += n;
                if (cb != null && total > 0) {
                    cb.onProgress((int) (got * 100L / total));
                }
            }
        } finally {
            c.disconnect();
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        if (!sb.toString().equals(expectedSha256)) {
            out.delete();
            throw new RuntimeException("контрольная сумма не совпала: " + out.getName());
        }
    }

    private static byte[] fetchRange(String url, long offset, int len) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            HttpURLConnection c = null;
            try {
                c = open(url, "GET", null);
                c.setReadTimeout(20000);
                c.setRequestProperty("Range", "bytes=" + offset + "-" + (offset + len - 1));
                int code = c.getResponseCode();
                if (code != 206 && code != 200) {
                    throw new IOException("HTTP " + code);
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream(len);
                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        bos.write(buf, 0, n);
                    }
                }
                byte[] data = bos.toByteArray();
                if (data.length != len) {
                    throw new IOException("кусок " + offset + ": получено " + data.length + " из " + len);
                }
                return data;
            } catch (Exception e) {
                last = e;
                if (attempt < 4) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        throw new RuntimeException("прервано");
                    }
                }
            } finally {
                if (c != null) {
                    c.disconnect();
                }
            }
        }
        throw new IOException("кусок " + offset + " не скачан: " + last, last);
    }

    /**
     * Fallback: сервер X-VPN (за Cloudflare) иногда отдаёт большие файлы
     * обрывком ~16 КБ и подвисает — прямое скачивание падает по read-timeout.
     * Range-куски по 16 КБ при этом проходят стабильно; качаем их параллельно.
     */
    public static void downloadChunked(String url, File out, String expectedSha256, ProgressCallback cb) throws Exception {
        long total;
        {
            HttpURLConnection c = open(url, "GET", null);
            c.setReadTimeout(20000);
            c.setRequestProperty("Range", "bytes=0-0");
            int code = c.getResponseCode();
            String contentRange = c.getHeaderField("Content-Range");
            c.disconnect();
            if (code != 206 || contentRange == null || !contentRange.contains("/")) {
                throw new IOException("сервер не поддерживает Range: HTTP " + code);
            }
            total = Long.parseLong(contentRange.substring(contentRange.lastIndexOf('/') + 1).trim());
            if (total <= 0) {
                throw new IOException("пустой файл");
            }
        }
        final int chunk = 16 * 1024;
        final int count = (int) ((total + chunk - 1) / chunk);
        final byte[][] pieces = new byte[count][];
        // Последовательные запросы со свежим соединением на каждый: сервер
        // (Cloudflare → origin) душит конкурентные запросы и не держит
        // keep-alive, а свежие одиночные Range-куски проходят по ~0.4 с.
        for (int i = 0; i < count; i++) {
            int len = (int) Math.min(chunk, total - (long) i * chunk);
            pieces[i] = fetchRange(url, (long) i * chunk, len);
            if (cb != null && (i + 1) % 25 == 0) {
                cb.onProgress((i + 1) * 100 / count);
            }
        }
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileOutputStream fo = new FileOutputStream(out)) {
            for (byte[] piece : pieces) {
                if (piece == null) {
                    throw new IOException("неполный файл");
                }
                fo.write(piece);
                md.update(piece);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        if (!sb.toString().equals(expectedSha256)) {
            out.delete();
            throw new RuntimeException("контрольная сумма не совпала: " + out.getName());
        }
    }

    /** Читает весь файл и возвращает SHA-256 hex (для сверки распакованного .so). */
    static String fileSha256(File f) throws Exception {        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    static byte[] readAll(File f) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
        }
        return bos.toByteArray();
    }
}
