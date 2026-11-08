package org.telegram.messenger.sessions;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Desktop TDF$ containers, Qt byte arrays and local MTProto 1 AES-IGE encryption. */
public final class TdataCodec {
    private static final byte[] MAGIC = {'T', 'D', 'F', '$'};
    private static final int VERSION = 3004000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private TdataCodec() {}

    public static List<SessionData> read(Map<String, byte[]> files, String passcode) throws Exception {
        ByteBuffer keyFile = ByteBuffer.wrap(container(find(files, "key_data")));
        byte[] salt = array(keyFile), encryptedKey = array(keyFile), encryptedInfo = array(keyFile);
        if (salt.length != 32) throw new IllegalArgumentException("Invalid tdata salt");
        byte[] passKey = localKey(salt, passcode);
        byte[] local = null;
        List<SessionData> result = new ArrayList<>();
        try {
            local = decrypt(encryptedKey, passKey);
            if (local.length != 256) throw new IllegalArgumentException("Invalid local key");
            ByteBuffer info = ByteBuffer.wrap(decrypt(encryptedInfo, local));
            int count = info.getInt();
            if (count < 1 || count > 100) throw new IllegalArgumentException("Invalid tdata account count");
            for (int i = 0; i < count; i++) {
                int index = info.getInt();
                if (index < 0 || index > 99) throw new IllegalArgumentException("Invalid account index");
                String name = dataName(index);
                ByteBuffer outer = ByteBuffer.wrap(container(find(files, name)));
                ByteBuffer data = ByteBuffer.wrap(decrypt(array(outer), local));
                // The account authorization file contains the MtpAuthorization block.
                if (data.getInt() != 0x4b) throw new IllegalArgumentException("Unsupported tdata authorization block");
                ByteBuffer auth = ByteBuffer.wrap(array(data));
                int oldUid = auth.getInt(), oldDc = auth.getInt();
                boolean wide = oldUid == -1 && oldDc == -1;
                long uid = wide ? auth.getLong() : Integer.toUnsignedLong(oldUid);
                int dc = wide ? auth.getInt() : oldDc;
                int keys = auth.getInt();
                if (keys < 1 || keys > 100) throw new IllegalArgumentException("Invalid key count");
                SessionData selected = null;
                for (int k = 0; k < keys; k++) {
                    int keyDc = auth.getInt();
                    byte[] key = new byte[256];
                    auth.get(key);
                    try {
                        if (keyDc == dc && selected == null) selected = new SessionData(dc, key, uid, 0);
                    } finally { Arrays.fill(key, (byte) 0); }
                }
                if (selected == null) throw new IllegalArgumentException("Missing main DC key");
                result.add(selected);
            }
            return result;
        } catch (Exception e) {
            for (SessionData s : result) s.clear();
            throw e;
        } finally {
            Arrays.fill(passKey, (byte) 0);
            if (local != null) Arrays.fill(local, (byte) 0);
        }
    }

    /** A minimal single-account tdata; Desktop reconstructs caches on first start. */
    public static Map<String, byte[]> write(SessionData session) throws Exception {
        if (session.userId == 0) throw new IllegalArgumentException("Missing user ID");
        byte[] salt = random(32), local = random(256), passKey = localKey(salt, "");
        try {
            byte[] info = ByteBuffer.allocate(12).putInt(1).putInt(0).putInt(0).array();
            byte[] key = join(qarray(salt), qarray(encrypt(local, passKey)), qarray(encrypt(info, local)));
            byte[] auth = ByteBuffer.allocate(288).putLong(-1L).putLong(session.userId).putInt(session.dcId)
                    .putInt(1).putInt(session.dcId).put(session.authKey).putInt(0).array();
            byte[] data = join(ByteBuffer.allocate(4).putInt(0x4b).array(), qarray(auth));
            Map<String, byte[]> files = new LinkedHashMap<>();
            files.put("tdata/key_datas", wrap(key));
            files.put("tdata/" + dataName(0) + "s", wrap(qarray(encrypt(data, local))));
            // Desktop/opentele load the local map before the authorization file.
            // An empty encrypted map is valid; caches/settings are rebuilt by Desktop.
            files.put("tdata/" + dataName(0) + "/maps", wrap(join(qarray(new byte[0]), qarray(new byte[0]),
                    qarray(encrypt(new byte[0], local)))));
            return files;
        } finally { Arrays.fill(local, (byte) 0); Arrays.fill(passKey, (byte) 0); }
    }

    private static String dataName(int index) throws Exception {
        byte[] md5 = digest("MD5", (index == 0 ? "data" : "data#" + (index + 1)).getBytes(StandardCharsets.UTF_8));
        StringBuilder s = new StringBuilder(16);
        for (int i = 0; i < 8; i++) { s.append("0123456789ABCDEF".charAt(md5[i] & 15)); s.append("0123456789ABCDEF".charAt((md5[i] & 255) >>> 4)); }
        return s.toString();
    }

    private static byte[] find(Map<String, byte[]> files, String name) throws Exception {
        for (String suffix : new String[]{"s", "1", "0"}) {
            byte[] value = files.get(name + suffix);
            if (value == null) value = files.get("tdata/" + name + suffix);
            if (value != null) {
                try { container(value); return value; } catch (IllegalArgumentException ignored) { }
            }
        }
        throw new IllegalArgumentException("Missing or damaged tdata file: " + name);
    }

    private static byte[] container(byte[] file) throws Exception {
        if (file.length < 24 || !Arrays.equals(MAGIC, Arrays.copyOf(file, 4))) throw new IllegalArgumentException("Invalid TDF header");
        byte[] body = Arrays.copyOfRange(file, 8, file.length - 16);
        byte[] check = digest("MD5", join(body, le(body.length), Arrays.copyOfRange(file, 4, 8), MAGIC));
        if (!MessageDigest.isEqual(check, Arrays.copyOfRange(file, file.length - 16, file.length))) throw new IllegalArgumentException("Invalid TDF checksum");
        return body;
    }

    private static byte[] wrap(byte[] body) throws Exception {
        return join(MAGIC, le(VERSION), body, digest("MD5", join(body, le(body.length), le(VERSION), MAGIC)));
    }

    private static byte[] array(ByteBuffer b) {
        int n = b.getInt();
        if (n < 0 || n > b.remaining()) throw new IllegalArgumentException("Invalid Qt byte array");
        byte[] result = new byte[n]; b.get(result); return result;
    }
    private static byte[] qarray(byte[] b) { return join(ByteBuffer.allocate(4).putInt(b.length).array(), b); }
    private static byte[] le(int n) { return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array(); }
    private static byte[] random(int n) { byte[] b = new byte[n]; RANDOM.nextBytes(b); return b; }
    private static byte[] digest(String type, byte[] b) throws Exception { return MessageDigest.getInstance(type).digest(b); }
    private static byte[] part(byte[] b, int start, int length) { return Arrays.copyOfRange(b, start, start + length); }
    private static byte[] join(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.write(p, 0, p.length);
        return out.toByteArray();
    }

    // PBKDF2 with binary password: PBEKeySpec would recode arbitrary digest bytes as text.
    private static byte[] localKey(byte[] salt, String passcode) throws Exception {
        if (passcode.length() > 1024) throw new IllegalArgumentException("Passcode too long");
        byte[] password = digest("SHA-512", join(salt, passcode.getBytes(StandardCharsets.UTF_8), salt));
        Mac mac = Mac.getInstance("HmacSHA512"); mac.init(new SecretKeySpec(password, "HmacSHA512"));
        byte[] key = new byte[256];
        int rounds = passcode.isEmpty() ? 1 : 100000;
        for (int block = 1; block <= 4; block++) {
            byte[] u = mac.doFinal(join(salt, ByteBuffer.allocate(4).putInt(block).array()));
            byte[] t = u.clone();
            for (int j = 1; j < rounds; j++) { u = mac.doFinal(u); for (int k = 0; k < 64; k++) t[k] ^= u[k]; }
            System.arraycopy(t, 0, key, (block - 1) * 64, 64);
        }
        Arrays.fill(password, (byte) 0);
        return key;
    }

    private static byte[] encrypt(byte[] data, byte[] key) throws Exception {
        int size = data.length + 4;
        byte[] plain = random((size + 15) & ~15);
        System.arraycopy(le(size), 0, plain, 0, 4); System.arraycopy(data, 0, plain, 4, data.length);
        byte[] msg = part(digest("SHA-1", plain), 0, 16);
        return join(msg, ige(plain, key, msg, true));
    }
    private static byte[] decrypt(byte[] data, byte[] key) throws Exception {
        if (data.length < 32 || data.length % 16 != 0) throw new IllegalArgumentException("Invalid encrypted size");
        byte[] msg = part(data, 0, 16), plain = ige(part(data, 16, data.length - 16), key, msg, false);
        if (!MessageDigest.isEqual(msg, part(digest("SHA-1", plain), 0, 16))) throw new IllegalArgumentException("Incorrect passcode or damaged tdata");
        int size = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (size < 4 || size > plain.length || size <= plain.length - 16) throw new IllegalArgumentException("Invalid decrypted size");
        return part(plain, 4, size - 4);
    }
    private static byte[] ige(byte[] data, byte[] key, byte[] msg, boolean encrypt) throws Exception {
        byte[] a = digest("SHA-1", join(msg, part(key, 8, 32)));
        byte[] b = digest("SHA-1", join(part(key, 40, 16), msg, part(key, 56, 16)));
        byte[] c = digest("SHA-1", join(part(key, 72, 32), msg));
        byte[] d = digest("SHA-1", join(msg, part(key, 104, 32)));
        byte[] aes = join(part(a, 0, 8), part(b, 8, 12), part(c, 4, 12));
        byte[] iv = join(part(a, 8, 12), part(b, 0, 8), part(c, 16, 4), part(d, 0, 8));
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(aes, "AES"));
        byte[] previousCipher = part(iv, 0, 16), previousPlain = part(iv, 16, 16), output = new byte[data.length];
        for (int offset = 0; offset < data.length; offset += 16) {
            byte[] input = part(data, offset, 16), block = input.clone();
            byte[] before = encrypt ? previousCipher : previousPlain;
            byte[] after = encrypt ? previousPlain : previousCipher;
            for (int i = 0; i < 16; i++) block[i] ^= before[i];
            block = cipher.doFinal(block);
            for (int i = 0; i < 16; i++) block[i] ^= after[i];
            System.arraycopy(block, 0, output, offset, 16);
            previousCipher = encrypt ? block : input; previousPlain = encrypt ? input : block;
        }
        return output;
    }
}
