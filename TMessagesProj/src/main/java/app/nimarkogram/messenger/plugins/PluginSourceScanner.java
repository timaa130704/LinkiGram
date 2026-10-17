package app.nimarkogram.messenger.plugins;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Heuristic, read-only review of Python plugin source before installation. */
public final class PluginSourceScanner {
    private static final int MAX_SOURCE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_BASE64_CANDIDATES = 32;
    private static final Pattern BASE64 = Pattern.compile("['\"]([A-Za-z0-9+/]{256,}={0,2})['\"]");

    public static final class Result {
        public final boolean readable;
        public final boolean network;
        public final boolean files;
        public final boolean dynamicCode;
        public final boolean encodedPayload;
        public final boolean dexPayload;

        private Result(boolean readable, boolean network, boolean files,
                       boolean dynamicCode, boolean encodedPayload, boolean dexPayload) {
            this.readable = readable;
            this.network = network;
            this.files = files;
            this.dynamicCode = dynamicCode;
            this.encodedPayload = encodedPayload;
            this.dexPayload = dexPayload;
        }

        public List<Integer> getFindings() {
            ArrayList<Integer> findings = new ArrayList<>();
            if (network) findings.add(org.telegram.messenger.R.string.PluginReviewNetwork);
            if (files) findings.add(org.telegram.messenger.R.string.PluginReviewFiles);
            if (dynamicCode) findings.add(org.telegram.messenger.R.string.PluginReviewDynamicCode);
            if (encodedPayload) findings.add(org.telegram.messenger.R.string.PluginReviewEncodedPayload);
            if (dexPayload) findings.add(org.telegram.messenger.R.string.PluginReviewDexPayload);
            return findings;
        }
    }

    private PluginSourceScanner() {
    }

    public static Result scan(String path) {
        if (path == null) return unknown();
        File source = new File(path);
        if (!source.isFile() || source.length() == 0 || source.length() > MAX_SOURCE_BYTES) return unknown();
        try {
            String text = readUtf8(source);
            if (text == null || text.isEmpty()) return unknown();
            String lower = text.toLowerCase(java.util.Locale.ROOT);
            boolean network = matches(lower, "\\b(requests|httpx|urllib|urlopen|socket)\\b");
            boolean files = matches(lower, "\\b(pathlib|shutil)\\b|\\bos\\.(path|remove|rename|unlink)\\b|\\bopen\\s*\\(");
            boolean dynamic = matches(lower, "\\b(exec|eval|compile)\\s*\\(|\\b__import__\\s*\\(|\\bimportlib\\b");
            boolean encoded = matches(lower, "\\b(base64\\.b64decode|binascii\\.a2b_base64|marshal\\.loads|zlib\\.decompress|gzip\\.decompress)\\b");
            boolean dex = matches(lower, "\\b(dexclassloader|inmemorydexclassloader|loaddex)\\b|classes\\.dex");
            if (!dex) {
                dex = containsEncodedDex(text);
            }
            return new Result(true, network, files, dynamic, encoded || dex, dex);
        } catch (Throwable ignore) {
            return unknown();
        }
    }

    private static Result unknown() {
        return new Result(false, false, false, false, false, false);
    }

    private static boolean matches(String value, String regex) {
        return Pattern.compile(regex).matcher(value).find();
    }

    private static String readUtf8(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString("UTF-8");
        }
    }

    private static boolean containsEncodedDex(String source) {
        Matcher matcher = BASE64.matcher(source);
        int checked = 0;
        while (matcher.find() && checked++ < MAX_BASE64_CANDIDATES) {
            String payload = matcher.group(1);
            if (payload.length() > 1024 * 1024) continue;
            try {
                byte[] decoded = Base64.decode(payload, Base64.DEFAULT);
                for (int i = 0; i + 8 <= decoded.length; i++) {
                    if (decoded[i] == 'd' && decoded[i + 1] == 'e'
                            && decoded[i + 2] == 'x' && decoded[i + 3] == '\n'
                            && decoded[i + 7] == 0) {
                        return true;
                    }
                }
            } catch (IllegalArgumentException ignore) {
                // A base64-looking literal can be ordinary data; keep scanning.
            }
        }
        return false;
    }
}
