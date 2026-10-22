package app.nimarkogram.messenger.banners;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

/** Fixed 3:1 crop matching the moderation server's 1200 x 400 banner. */
public final class BannerCropActivity extends BaseFragment {
    public interface Delegate { void submit(File file); }

    private final Bitmap bitmap;
    private final File source;
    private final Delegate delegate;
    private CropView cropView;
    private boolean saving;
    private volatile boolean destroyed;

    public BannerCropActivity(Bitmap bitmap, File source, Delegate delegate) {
        this.bitmap = bitmap;
        this.source = source;
        this.delegate = delegate;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackgroundColor(Color.BLACK);
        actionBar.setTitleColor(Color.WHITE);
        actionBar.setItemsColor(Color.WHITE, false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.NM_BAN_CropTitle));
        actionBar.createMenu().addItemWithWidth(1, R.drawable.ic_ab_done,
                AndroidUtilities.dp(56), LocaleController.getString(R.string.NM_BAN_SubmitModeration));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == 1) saveCrop();
            }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Color.BLACK);
        frame.addView(cropView = new CropView(context), LayoutHelper.createFrame(-1, -1));
        TextView hint = new TextView(context);
        hint.setText(LocaleController.getString(R.string.NM_BAN_CropHint));
        hint.setTextSize(14);
        hint.setTextColor(Color.WHITE);
        hint.setGravity(Gravity.CENTER);
        frame.addView(hint, LayoutHelper.createFrame(-1, -2, Gravity.BOTTOM, 24, 0, 24, 28));
        return fragmentView = frame;
    }

    private void saveCrop() {
        if (saving || cropView == null || cropView.area.width() <= 0 || destroyed) return;
        saving = true;
        // Freeze geometry now: gestures or rotation must not change the saved selection.
        float outputScale = 1200f / cropView.area.width();
        float scale = cropView.scale * outputScale;
        float left = (cropView.left - cropView.area.left) * outputScale;
        float top = (cropView.top - cropView.area.top) * outputScale;
        Utilities.globalQueue.postRunnable(() -> {
            File output = null;
            Bitmap result = null;
            try {
                result = Bitmap.createBitmap(1200, 400, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(result);
                canvas.translate(left, top);
                canvas.scale(scale, scale);
                canvas.drawBitmap(bitmap, 0, 0, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
                output = File.createTempFile("banner_crop_", ".png", source.getParentFile());
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    if (!result.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw new java.io.IOException("Could not encode cropped banner");
                    }
                }
                File ready = output;
                AndroidUtilities.runOnUIThread(() -> {
                    if (destroyed) { ready.delete(); release(); return; }
                    saving = false;
                    delegate.submit(ready);
                    finishFragment();
                });
            } catch (Exception | OutOfMemoryError e) {
                if (output != null) output.delete();
                android.util.Log.e("LinkiBannerUpload", "Could not crop banner", e);
                AndroidUtilities.runOnUIThread(() -> {
                    saving = false;
                    if (destroyed) { release(); return; }
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                            LocaleController.getString(R.string.NM_BAN_CropFailed)).show();
                });
            } finally {
                if (result != null) result.recycle();
            }
        });
    }

    /** Also called if the navigation stack could not present this fragment. */
    public void release() {
        source.delete();
        if (!bitmap.isRecycled()) bitmap.recycle();
    }

    @Override public void onFragmentDestroy() {
        destroyed = true;
        super.onFragmentDestroy();
        if (!saving) release(); // An in-flight worker owns the bitmap until its UI callback.
    }

    @Override public boolean isSwipeBackEnabled(MotionEvent event) { return false; }

    private final class CropView extends View {
        private final RectF area = new RectF();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final ScaleGestureDetector zoom;
        private float scale, minimum, left, top, lastX, lastY;

        CropView(Context context) {
            super(context);
            zoom = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector detector) {
                    if (saving) return false;
                    float next = Math.max(minimum, Math.min(minimum * 8, scale * detector.getScaleFactor()));
                    float ratio = next / scale;
                    left = detector.getFocusX() - (detector.getFocusX() - left) * ratio;
                    top = detector.getFocusY() - (detector.getFocusY() - top) * ratio;
                    scale = next;
                    constrain(); invalidate(); return true;
                }
            });
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            float focusX = scale > 0 ? (area.centerX() - left) / scale : bitmap.getWidth() / 2f;
            float focusY = scale > 0 ? (area.centerY() - top) / scale : bitmap.getHeight() / 2f;
            float zoomFactor = minimum > 0 ? scale / minimum : 1;
            float width = Math.max(1, Math.min(w - AndroidUtilities.dp(32), (h - AndroidUtilities.dp(100)) * 3f));
            area.set((w - width) / 2f, (h - width / 3f) / 2f,
                    (w + width) / 2f, (h + width / 3f) / 2f);
            minimum = Math.max(area.width() / bitmap.getWidth(), area.height() / bitmap.getHeight());
            scale = minimum * zoomFactor;
            left = area.centerX() - focusX * scale;
            top = area.centerY() - focusY * scale;
            constrain();
        }

        private void constrain() {
            left = Math.min(area.left, Math.max(area.right - bitmap.getWidth() * scale, left));
            top = Math.min(area.top, Math.max(area.bottom - bitmap.getHeight() * scale, top));
        }

        @Override protected void onDraw(Canvas canvas) {
            if (destroyed || bitmap.isRecycled()) return;
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
            canvas.drawBitmap(bitmap, null,
                    new RectF(left, top, left + bitmap.getWidth() * scale, top + bitmap.getHeight() * scale), paint);
            paint.setColor(0xbb000000);
            canvas.drawRect(0, 0, getWidth(), area.top, paint);
            canvas.drawRect(0, area.bottom, getWidth(), getHeight(), paint);
            canvas.drawRect(0, area.top, area.left, area.bottom, paint);
            canvas.drawRect(area.right, area.top, getWidth(), area.bottom, paint);
            paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(1)); canvas.drawRect(area, paint);
            paint.setAlpha(90);
            for (int i = 1; i < 3; i++) {
                float x = area.left + area.width() * i / 3f, y = area.top + area.height() * i / 3f;
                canvas.drawLine(x, area.top, x, area.bottom, paint);
                canvas.drawLine(area.left, y, area.right, y, paint);
            }
            paint.setAlpha(255); paint.setStyle(Paint.Style.FILL);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (saving) return true;
            zoom.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && event.getPointerCount() == 1
                    && !zoom.isInProgress()) {
                left += event.getX() - lastX; top += event.getY() - lastY;
                constrain(); invalidate();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            // Track the surviving pointer so a pinch ending does not jump the image.
            int index = event.getActionMasked() == MotionEvent.ACTION_POINTER_UP && event.getActionIndex() == 0
                    && event.getPointerCount() > 1 ? 1 : 0;
            lastX = event.getX(index); lastY = event.getY(index);
            return true;
        }
    }
}
