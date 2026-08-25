package app.nimarkogram.messenger;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class NimarkoWallpaper {

    public static final String FILE_NAME = "nimarko_bg.jpg";
    private static volatile Bitmap cached;
    private static String cachedPath;
    private static long lastLog;

    private NimarkoWallpaper() {}

    public static File getFile() {
        return new File(ApplicationLoader.getFilesDirFixed(), FILE_NAME);
    }

    public static boolean hasImage() {
        return getFile().exists();
    }

    public static boolean isEnabled() {
        return NimarkoConfig.customBgEnabled && hasImage();
    }

    public static Bitmap getBitmap() {
        if (!NimarkoConfig.customBgEnabled) return null;
        String path = NimarkoConfig.customBgPath;
        if (path == null || path.isEmpty()) return null;
        Bitmap local = cached;
        if (local != null && !local.isRecycled() && path.equals(cachedPath)) {
            return local;
        }
        synchronized (NimarkoWallpaper.class) {
            if (cached != null && !cached.isRecycled() && path.equals(cachedPath)) {
                return cached;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) {
                return null;
            }
            int maxSide = Math.max(org.telegram.messenger.AndroidUtilities.displaySize.x, org.telegram.messenger.AndroidUtilities.displaySize.y);
            int sample = 1;
            while ((o.outWidth / sample) > maxSide * 1.5f || (o.outHeight / sample) > maxSide * 1.5f) {
                sample *= 2;
            }
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeFile(path, o2);
            if (bmp == null) return null;
            if (bmp.getConfig() != Bitmap.Config.ARGB_8888) {
                Bitmap copy = bmp.copy(Bitmap.Config.ARGB_8888, false);
                bmp.recycle();
                bmp = copy;
            }
            cached = bmp;
            cachedPath = path;
            return bmp;
        }
    }

    public static void invalidateCache() {
        synchronized (NimarkoWallpaper.class) {
            cached = null;
            cachedPath = null;
        }
    }

    public static void saveFromUri(Uri uri) throws Throwable {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream bin = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
            if (bin == null) throw new IllegalStateException("no stream");
            BitmapFactory.decodeStream(bin, null, o);
        }
        if (o.outWidth <= 0 || o.outHeight <= 0) throw new IllegalStateException("decode bounds failed");
        InputStream in2 = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
        int maxSide = Math.max(org.telegram.messenger.AndroidUtilities.displaySize.x, org.telegram.messenger.AndroidUtilities.displaySize.y);
        int sample = 1;
        while ((o.outWidth / sample) > maxSide * 1.5f || (o.outHeight / sample) > maxSide * 1.5f) {
            sample *= 2;
        }
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeStream(in2, null, o2);
        try { in2.close(); } catch (Throwable ignored) {}
        if (bmp == null) throw new IllegalStateException("decode failed");
        android.util.Log.d("nimarko-bg", "saveFromUri decoded=" + bmp.getWidth() + "x" + bmp.getHeight()
                + " sample=" + sample + " displaySize=" + org.telegram.messenger.AndroidUtilities.displaySize.x + "x" + org.telegram.messenger.AndroidUtilities.displaySize.y);
        File dst = getFile();
        FileOutputStream out = new FileOutputStream(dst);
        bmp.compress(Bitmap.CompressFormat.JPEG, 88, out);
        out.flush();
        out.close();
        if (bmp.getConfig() != Bitmap.Config.ARGB_8888) {
            Bitmap copy = bmp.copy(Bitmap.Config.ARGB_8888, false);
            bmp.recycle();
            bmp = copy;
        }
        synchronized (NimarkoWallpaper.class) {
            if (cached != null && cached != bmp && !cached.isRecycled()) cached.recycle();
            cached = bmp;
            cachedPath = dst.getAbsolutePath();
        }
    }

    public static void removeImage() {
        //noinspection ResultOfMethodCallIgnored
        getFile().delete();
        invalidateCache();
    }

    public static class WallpaperView extends View {
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final Paint scrimPaint = new Paint();
        private final android.graphics.RectF dstRect = new android.graphics.RectF();

        public WallpaperView(android.content.Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Bitmap bmp = getBitmap();
            if (bmp == null || bmp.isRecycled()) {
                canvas.drawColor(Color.TRANSPARENT);
                return;
            }
            float vw = getWidth(), vh = getHeight();
            if (vw <= 0 || vh <= 0) return;
            float bw = bmp.getWidth(), bh = bmp.getHeight();
            float scale = Math.max(vw / bw, vh / bh);
            float dw = bw * scale, dh = bh * scale;
            float left = (vw - dw) / 2f, top = (vh - dh) / 2f;
            int saveToRestore = canvas.save();
            canvas.clipRect(0, 0, vw, vh);
            dstRect.set(left, top, left + dw, top + dh);
            canvas.drawBitmap(bmp, null, dstRect, paint);
            int dimPercent = NimarkoConfig.customBgDim;
            if (dimPercent > 0) {
                scrimPaint.setColor(Color.argb(Math.min(230, (int) (dimPercent * 2.55f)), 0, 0, 0));
                canvas.drawRect(0, 0, vw, vh, scrimPaint);
            }
            canvas.restoreToCount(saveToRestore);
        }
    }
}
