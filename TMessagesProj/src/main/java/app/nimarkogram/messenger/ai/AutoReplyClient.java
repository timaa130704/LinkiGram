package app.nimarkogram.messenger.ai;

import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** OpenAI-compatible text requests. Call exclusively from a worker thread. */
public final class AutoReplyClient {
    private static final long CACHE_AGE = 7L * 24 * 60 * 60 * 1000;
    private static final int MAX_RESPONSE = 1024 * 1024;
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ai-request-deadline");
        thread.setDaemon(true);
        return thread;
    });
    private static JSONObject measurements;
    private AutoReplyClient() { }

    public static final class ClientException extends Exception {
        public final int status;
        public ClientException(String message) { this(message, 0); }
        public ClientException(String message, int status) { super(message); this.status = status; }
    }

    public static String normalizeBase(String base) throws ClientException {
        try {
            URI uri = new URI(base == null ? "" : base.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535))) {
                throw new IllegalArgumentException();
            }
            String value = uri.normalize().toASCIIString();
            while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
            if (value.endsWith("/chat/completions")) value = value.substring(0, value.length() - 17);
            else if (value.endsWith("/models")) value = value.substring(0, value.length() - 7);
            return value;
        } catch (Exception e) {
            throw new ClientException("Укажите корректный HTTPS base URL без параметров, пароля и фрагмента (например https://api.openai.com/v1).");
        }
    }

    public static List<String> models(String base, String key) throws ClientException {
        JSONObject result = request(base, key, "models", null, 20000);
        JSONArray data = result.optJSONArray("data");
        if (data == null) throw new ClientException("Провайдер вернул неподдерживаемый список моделей. Введите model ID вручную.");
        ArrayList<String> ids = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            String id = item == null ? "" : item.optString("id", "").trim();
            if (!id.isEmpty() && id.length() <= 256 && !ids.contains(id)) ids.add(id);
            if (ids.size() >= 200) break;
        }
        if (ids.isEmpty()) throw new ClientException("Список моделей пуст. Введите model ID вручную.");
        Collections.sort(ids);
        return ids;
    }

    public static String generate(String base, String key, String model, JSONArray messages, int maxChars) throws ClientException {
        try {
            return generate(base, key, model, messages, maxChars, 30000);
        } catch (ClientException error) {
            if (error.status == 400 || error.status == 404 || error.status == 422) {
                record(normalizeBase(base), model, -1);
            }
            throw error;
        }
    }

    private static String generate(String base, String key, String model, JSONArray messages, int maxChars, int timeout) throws ClientException {
        if (model == null || model.trim().isEmpty()) throw new ClientException("Выберите модель или введите model ID.");
        try {
            JSONObject body = new JSONObject().put("model", model.trim()).put("messages", messages)
                    .put("stream", false).put("max_tokens", Math.max(16, Math.min(2048, maxChars)));
            long started = SystemClock.elapsedRealtime();
            JSONObject result;
            try {
                result = request(base, key, "chat/completions", body, timeout);
            } catch (ClientException error) {
                int remaining = (int) (timeout - (SystemClock.elapsedRealtime() - started));
                if (error.status != 400 || remaining <= 0) throw error;
                // Newer reasoning models accept max_completion_tokens instead of max_tokens.
                Object tokens = body.remove("max_tokens");
                body.put("max_completion_tokens", tokens);
                result = request(base, key, "chat/completions", body, remaining);
            }
            JSONArray choices = result.optJSONArray("choices");
            JSONObject choice = choices == null ? null : choices.optJSONObject(0);
            JSONObject message = choice == null ? null : choice.optJSONObject("message");
            Object content = message == null ? null : message.opt("content");
            if (!(content instanceof String) || ((String) content).trim().isEmpty()) {
                throw new ClientException("Модель не вернула текстовый ответ. Выберите совместимую chat-модель.");
            }
            String reply = ((String) content).trim();
            int limit = Math.max(1, maxChars);
            if (reply.length() > limit) {
                if (Character.isHighSurrogate(reply.charAt(limit - 1))) limit--;
                reply = reply.substring(0, limit).trim();
            }
            return reply;
        } catch (ClientException e) { throw e; }
        catch (Exception e) { throw new ClientException("Не удалось обработать ответ ИИ."); }
    }

    /** Synthetic prompt only; no chat text is used to measure generation latency. */
    public static Map<String, Long> benchmark(String base, String key, List<String> ids) throws ClientException {
        base = normalizeBase(base);
        Map<String, Long> result = new LinkedHashMap<>();
        int count = 0;
        long deadline = SystemClock.elapsedRealtime() + 90000;
        for (String model : ids) {
            if (Thread.currentThread().isInterrupted() || SystemClock.elapsedRealtime() >= deadline) break;
            long cached = measurement(base, model);
            if (cached != Long.MAX_VALUE) { result.put(model, cached); continue; }
            if (count++ >= 12) break;
            long elapsed;
            try {
                JSONArray prompt = new JSONArray().put(new JSONObject().put("role", "user").put("content", "Reply only: OK"));
                long started = SystemClock.elapsedRealtime();
                generate(base, key, model, prompt, 16, (int) Math.min(12000, Math.max(1, deadline - SystemClock.elapsedRealtime())));
                elapsed = Math.max(1, SystemClock.elapsedRealtime() - started);
            } catch (ClientException e) {
                if (e.status == 401 || e.status == 403 || e.status == 429) throw e;
                elapsed = -1;
            } catch (Exception e) {
                elapsed = -1;
            }
            if (Thread.currentThread().isInterrupted()) break;
            record(base, model, elapsed);
            result.put(model, elapsed);
        }
        return result;
    }

    public static List<String> ranked(String base, List<String> ids) {
        ArrayList<String> sorted = new ArrayList<>(ids);
        try {
            String normalized = normalizeBase(base);
            sorted.sort((a, b) -> Long.compare(rank(measurement(normalized, a)), rank(measurement(normalized, b))));
        } catch (ClientException ignored) { }
        return sorted;
    }

    private static long rank(long value) { return value < 0 ? Long.MAX_VALUE : value == Long.MAX_VALUE ? Long.MAX_VALUE - 1 : value; }
    private static File cacheFile() { return new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "ai-model-speed.json"); }
    private static synchronized JSONObject cache() {
        if (measurements == null) {
            measurements = new JSONObject();
            try (FileInputStream in = new FileInputStream(cacheFile())) {
                measurements = new JSONObject(read(in));
            } catch (Exception ignored) { }
        }
        return measurements;
    }
    private static String cacheKey(String base, String model) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((base + "\n" + model).getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder();
            for (byte b : hash) value.append(String.format(java.util.Locale.US, "%02x", b & 255));
            return value.toString();
        } catch (Exception e) { return ""; }
    }
    private static synchronized long measurement(String base, String model) {
        JSONObject item = cache().optJSONObject(cacheKey(base, model));
        long now = System.currentTimeMillis();
        if (item == null || now < item.optLong("at") || now - item.optLong("at") > CACHE_AGE) return Long.MAX_VALUE;
        return item.optLong("ms", Long.MAX_VALUE);
    }
    private static synchronized void record(String base, String model, long elapsed) {
        try {
            cache().put(cacheKey(base, model), new JSONObject().put("at", System.currentTimeMillis()).put("ms", elapsed));
            try (FileOutputStream out = new FileOutputStream(cacheFile())) {
                out.write(cache().toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { }
    }
    public static synchronized void reset() { measurements = new JSONObject(); cacheFile().delete(); }

    private static JSONObject request(String base, String key, String path, JSONObject body, int timeout) throws ClientException {
        HttpURLConnection connection = null;
        ScheduledFuture<?> deadline = null;
        try {
            String normalized = normalizeBase(base);
            if (key == null || key.trim().isEmpty()) throw new ClientException("Введите API key.");
            if (key.indexOf('\r') >= 0 || key.indexOf('\n') >= 0) throw new ClientException("API key содержит недопустимые символы.");
            connection = (HttpURLConnection) new URL(normalized + "/" + path).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(Math.min(timeout, 10000));
            connection.setReadTimeout(timeout);
            connection.setUseCaches(false);
            connection.setRequestProperty("Authorization", "Bearer " + key.trim());
            connection.setRequestProperty("Accept", "application/json");
            final HttpURLConnection active = connection;
            deadline = DEADLINES.schedule(active::disconnect, timeout, TimeUnit.MILLISECONDS);
            if (body != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                String message = status == 401 || status == 403 ? "Провайдер отклонил API key или доступ к модели."
                        : status == 429 ? "Лимит запросов провайдера исчерпан. Попробуйте позже."
                        : status >= 300 && status < 400 ? "Endpoint перенаправляет запрос. Укажите конечный HTTPS base URL."
                        : status == 404 ? "Endpoint или модель не найдены. Проверьте base URL и model ID."
                        : status == 400 || status == 422 ? "Модель не поддерживает этот chat-запрос. Выберите другую модель."
                        : "Ошибка провайдера (HTTP " + status + "). Попробуйте позже.";
                throw new ClientException(message, status);
            }
            try (InputStream in = connection.getInputStream()) { return new JSONObject(read(in)); }
        } catch (ClientException e) { throw e; }
        catch (SocketTimeoutException e) { throw new ClientException("ИИ не ответил вовремя. Попробуйте другую модель."); }
        catch (org.json.JSONException e) { throw new ClientException("Провайдер вернул неподдерживаемый формат JSON."); }
        catch (Exception e) { throw new ClientException("Не удалось подключиться к ИИ. Проверьте сеть, base URL и доступность провайдера."); }
        finally {
            if (deadline != null) deadline.cancel(false);
            if (connection != null) connection.disconnect();
        }
    }
    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = in.read(buffer)) != -1) {
            if (out.size() + count > MAX_RESPONSE) throw new ClientException("Ответ провайдера слишком большой.");
            out.write(buffer, 0, count);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
