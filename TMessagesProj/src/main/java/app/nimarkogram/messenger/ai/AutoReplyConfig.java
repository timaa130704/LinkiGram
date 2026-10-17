package app.nimarkogram.messenger.ai;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Isolated from portable preferences and Android backup, including device transfer. */
public final class AutoReplyConfig {
    private static final java.util.Map<Integer, AutoReplyConfig> INSTANCES = new java.util.HashMap<>();
    public static synchronized AutoReplyConfig get(int account) {
        AutoReplyConfig c = INSTANCES.get(account);
        if (c == null) { c = new AutoReplyConfig(account); INSTANCES.put(account, c); }
        return c;
    }
    private final AtomicFile file;
    private final String alias;
    private JSONObject data = new JSONObject();
    private boolean unreadable;
    private AutoReplyConfig(int account) {
        file = new AtomicFile(new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "ai_autoreply_" + account));
        alias = "linkigram_ai_autoreply_" + account;
        if (file.getBaseFile().exists()) {
            try {
                byte[] bytes = file.readFully();
                if (bytes.length < 29) throw new Exception();
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, secret(), new GCMParameterSpec(128, java.util.Arrays.copyOf(bytes, 12)));
                data = new JSONObject(new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8));
            } catch (Exception e) { unreadable = true; }
        }
    }
    private SecretKey secret() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(alias)) return (SecretKey) store.getKey(alias, null);
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return gen.generateKey();
    }
    private void persist(JSONObject next) {
        if (unreadable) throw new IllegalStateException("Не удалось открыть защищённые настройки ИИ. Перезапустите приложение.");
        FileOutputStream stream = null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, secret());
            byte[] encrypted = cipher.doFinal(next.toString().getBytes(StandardCharsets.UTF_8));
            stream = file.startWrite(); stream.write(cipher.getIV()); stream.write(encrypted); file.finishWrite(stream);
            data = next;
        } catch (Exception e) {
            if (stream != null) file.failWrite(stream);
            throw new IllegalStateException("Не удалось сохранить защищённые настройки ИИ.");
        }
    }
    public synchronized String getString(String key, String fallback) { return data.optString(key, fallback); }
    public synchronized int getInt(String key, int fallback) { return data.optInt(key, fallback); }
    public synchronized boolean getBoolean(String key, boolean fallback) { return data.optBoolean(key, fallback); }
    private synchronized void put(String key, Object value) {
        try {
            JSONObject next = new JSONObject(data.toString());
            next.put(key, value);
            if (key.equals("base") && !String.valueOf(value).equals(data.optString("base", "https://api.openai.com/v1"))) {
                java.util.Iterator<String> keys = next.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    JSONObject chat = name.startsWith("chat_") ? next.optJSONObject(name) : null;
                    if (chat != null) {
                        chat.put("enabled", false);
                        chat.put("generation", chat.optLong("generation") + 1);
                    }
                }
            }
            if (key.equals("base") || key.equals("api_key") || key.equals("model") || key.equals("auto") || key.equals("models")) {
                next.put("connection_revision", data.optLong("connection_revision") + 1);
            }
            persist(next);
        } catch (org.json.JSONException e) {
            throw new IllegalArgumentException("Некорректная настройка ИИ.");
        }
    }
    public void putString(String key, String value) { put(key, value); }
    public void putInt(String key, int value) { put(key, value); }
    public void putBoolean(String key, boolean value) { put(key, value); }
    public synchronized long connectionRevision() { return data.optLong("connection_revision"); }
    public synchronized String key() throws Exception {
        if (unreadable) throw new Exception("Защищённый ключ недоступен.");
        return getString("api_key", "");
    }
    public void setKey(String value) throws Exception { putString("api_key", value); }
    public synchronized JSONObject chat(long did) {
        try { return new JSONObject(data.optJSONObject("chat_" + did) == null ? "{}" : data.getJSONObject("chat_" + did).toString()); }
        catch (Exception e) { return new JSONObject(); }
    }
    public synchronized void saveChat(long did, JSONObject chat) throws Exception {
        JSONObject copy = new JSONObject(chat.toString());
        copy.put("generation", chat(did).optLong("generation", 0) + 1);
        if (copy.optBoolean("enabled") && !chat(did).optBoolean("enabled")) {
            copy.put("owner", org.telegram.messenger.UserConfig.getInstance(account()).getClientUserId());
            copy.put("enabled_at", org.telegram.tgnet.ConnectionsManager.getInstance(account()).getCurrentTime());
        }
        put("chat_" + did, copy);
    }
    private int account() { return Integer.parseInt(alias.substring(alias.lastIndexOf('_') + 1)); }
}
