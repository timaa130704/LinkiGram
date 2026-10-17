package app.nimarkogram.messenger.icons;

import android.content.Context;
import android.net.Uri;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SvgHelper;
import org.telegram.ui.ActionBar.Theme;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Imports and renders user supplied, resource-name keyed SVG icon packs. */
public final class CustomSvgIconPack {
    private static final String DIRECTORY = "custom_svg_icons";
    private static final int MAX_FILES = 300;
    private static final int MAX_ICON_BYTES = 512 * 1024;
    private static final int MAX_TOTAL_BYTES = 5 * 1024 * 1024;
    private static volatile JSONObject cachedManifest;

    private CustomSvgIconPack() {
    }

    private static File root(Context context) {
        return new File(context.getFilesDir(), DIRECTORY);
    }

    public static boolean isInstalled(Context context) {
        return new File(root(context), "manifest.json").isFile();
    }

    public static synchronized void importZip(Context context, Uri uri) throws Exception {
        File staging = new File(context.getFilesDir(), DIRECTORY + ".staging");
        deleteTree(staging);
        if (!staging.mkdirs()) {
            throw new IllegalStateException("Cannot create SVG pack staging directory");
        }

        int files = 0;
        long totalBytes = 0;
        InputStream raw = context.getContentResolver().openInputStream(uri);
        if (raw == null) {
            deleteTree(staging);
            throw new IllegalArgumentException("Cannot open the selected ZIP file");
        }
        try (ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!("manifest.json".equals(name)
                        || name.matches("icons/[A-Za-z][A-Za-z0-9_-]{0,63}\\.svg"))) {
                    throw new IllegalArgumentException("Unexpected file in SVG pack: " + name);
                }
                if (++files > MAX_FILES) {
                    throw new IllegalArgumentException("SVG pack has too many files");
                }
                File out = new File(staging, name);
                File parent = out.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IllegalStateException("Cannot create SVG icon directory");
                }
                int entryBytes = 0;
                try (FileOutputStream stream = new FileOutputStream(out)) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        entryBytes += read;
                        totalBytes += read;
                        if (entryBytes > MAX_ICON_BYTES || totalBytes > MAX_TOTAL_BYTES) {
                            throw new IllegalArgumentException("SVG pack exceeds the size limit");
                        }
                        stream.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
            validatePack(staging);
        } catch (Exception error) {
            deleteTree(staging);
            throw error;
        }

        File destination = root(context);
        File backup = new File(context.getFilesDir(), DIRECTORY + ".backup");
        deleteTree(backup);
        if (destination.exists() && !destination.renameTo(backup)) {
            deleteTree(staging);
            throw new IllegalStateException("Cannot replace the existing SVG pack");
        }
        if (!staging.renameTo(destination)) {
            if (backup.exists()) backup.renameTo(destination);
            deleteTree(staging);
            throw new IllegalStateException("Cannot install the SVG pack");
        }
        deleteTree(backup);
        cachedManifest = null;
    }

    public static Drawable load(Resources resources, int resourceId) {
        try {
            JSONObject manifest = manifest();
            if (manifest == null) return null;
            String resourceName = resources.getResourceEntryName(resourceId);
            String filename = manifest.getJSONObject("icons").optString(resourceName, null);
            if (filename == null || !filename.matches("icons/[A-Za-z][A-Za-z0-9_-]{0,63}\\.svg")) {
                return null;
            }
            Context context = org.telegram.messenger.ApplicationLoader.applicationContext;
            File file = new File(root(context), filename);
            if (!file.isFile() || file.length() > MAX_ICON_BYTES) return null;
            String svg = readText(file);
            if (!isSafeSvg(svg)) return null;
            SvgHelper.SvgDrawable drawable = SvgHelper.getDrawable(svg);
            if (drawable != null) {
                drawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon));
                return drawable.clone();
            }
            return null;
        } catch (Throwable error) {
            FileLog.e(error);
            return null;
        }
    }

    private static JSONObject manifest() {
        JSONObject loaded = cachedManifest;
        if (loaded != null) return loaded;
        try {
            File file = new File(root(org.telegram.messenger.ApplicationLoader.applicationContext), "manifest.json");
            if (!file.isFile() || file.length() > MAX_ICON_BYTES) return null;
            loaded = new JSONObject(readText(file));
            if (loaded.optJSONObject("icons") == null) return null;
            cachedManifest = loaded;
            return loaded;
        } catch (Throwable error) {
            FileLog.e(error);
            return null;
        }
    }

    private static void validatePack(File directory) throws Exception {
        File manifestFile = new File(directory, "manifest.json");
        if (!manifestFile.isFile() || manifestFile.length() > MAX_ICON_BYTES) {
            throw new IllegalArgumentException("SVG pack must contain a small manifest.json");
        }
        JSONObject manifest = new JSONObject(readText(manifestFile));
        JSONObject icons = manifest.optJSONObject("icons");
        if (icons == null || icons.length() == 0 || icons.length() > MAX_FILES - 1) {
            throw new IllegalArgumentException("Manifest must map icon resource names to SVG files");
        }
        java.util.Iterator<String> keys = icons.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String filename = icons.getString(key);
            if (!key.matches("[A-Za-z][A-Za-z0-9_]{0,127}")
                    || !filename.matches("icons/[A-Za-z][A-Za-z0-9_-]{0,63}\\.svg")) {
                throw new IllegalArgumentException("Invalid SVG icon mapping");
            }
            File icon = new File(directory, filename);
            if (!icon.isFile() || icon.length() == 0 || icon.length() > MAX_ICON_BYTES
                    || !isSafeSvg(readText(icon))) {
                throw new IllegalArgumentException("Invalid or unsupported SVG: " + filename);
            }
        }
    }

    private static boolean isSafeSvg(String svg) {
        if (svg == null || svg.length() > MAX_ICON_BYTES) return false;
        String lower = svg.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("<svg") && !lower.contains("<!doctype") && !lower.contains("<!entity")
                && !lower.contains("<script") && !lower.contains("javascript:")
                && !lower.contains("onload=") && !lower.contains("href=")
                && !lower.contains("url(");
    }

    private static String readText(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString("UTF-8");
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }
}
