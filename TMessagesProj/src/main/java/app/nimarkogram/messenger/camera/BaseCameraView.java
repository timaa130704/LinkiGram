package app.nimarkogram.messenger.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.camera.CameraSessionWrapper;
import org.telegram.messenger.camera.CameraView;

import java.io.File;
import java.util.Collections;
import java.util.List;

public abstract class BaseCameraView extends FrameLayout {

    public interface CameraReadyCallback { void onCameraReady(); }

    public interface SavedCallback { void onSaved(boolean success); }

    public interface VideoSavedCallback {
        void onFinishVideoRecording(String thumbPath, long duration);
    }

    public BaseCameraView(Context context) {
        super(context);
    }

    public BaseCameraView(Context context, @Nullable android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public BaseCameraView(Context context, @Nullable android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void initCamera() {   }

    public void destroyCamera() {   }

    public void destroy(boolean async, @Nullable Runnable beforeDestroyRunnable) {
        destroyCamera();
        if (beforeDestroyRunnable != null) beforeDestroyRunnable.run();
    }

    public abstract boolean isInited();
    public boolean isFrontface() { return false; }
    public boolean hasFrontFaceCamera() { return false; }

    public void takePicture(File output, @Nullable SavedCallback cb) {
        if (cb != null) cb.onSaved(false);
    }

    public void takePicture(File file, @Nullable Runnable onTake) {
        takePicture(file, success -> { if (onTake != null) onTake.run(); });
    }

    public void startTakePictureAnimation(boolean haptic) {   }

    public void recordVideo(File path, boolean mirrorThumb, @Nullable VideoSavedCallback onStop) {
         
    }

    public void stopVideoRecording(boolean abandon) {   }

    public void setRecordFile(File generateVideoPath) {   }

    public void switchCamera() {   }
    public abstract void setZoom(float value);
    public float getZoom() { return 0f; }
    public float resetZoom() { setZoom(0f); return 0f; }

    // LinkiGram: camera module switching (0.6x / 1.0x / 1.9x and whatever else the
    // phone actually has). Empty by default so every camera implementation keeps
    // working; the ones that can switch override these.

    /** The modules this camera exposes, widest first. Never null. */
    public List<CameraLensSelector.Lens> getAvailableLenses() {
        return Collections.singletonList(new CameraLensSelector.Lens(1f, 1f, 0f));
    }

    /** True when this camera has more than one module, so the switcher is worth showing. */
    public boolean hasMultipleLenses() {
        return CameraLensSelector.hasMultipleLenses(getAvailableLenses());
    }

    /** Switches to the given module. Returns false when the switch was not applied. */
    public boolean setLens(@NonNull CameraLensSelector.Lens lens) { return false; }

    /** The module currently in use. */
    public CameraLensSelector.Lens getActiveLens() {
        return CameraLensSelector.closestTo(getAvailableLenses(), 1f);
    }

    /**
     * Cycles to the next module and returns it, or null when there is nothing to
     * switch to. This is what a tap on the lens button calls.
     */
    @Nullable public CameraLensSelector.Lens switchToNextLens() {
        List<CameraLensSelector.Lens> lenses = getAvailableLenses();
        if (!CameraLensSelector.hasMultipleLenses(lenses)) return null;
        CameraLensSelector.Lens next = CameraLensSelector.next(lenses, getActiveLens());
        if (next != null && setLens(next)) {
            return next;
        }
        return null;
    }
    public void setExposureCompensation(float value0to1) {   }
    public boolean isExposureCompensationSupported() { return false; }
    public void focusToPoint(int x, int y) {   }

    public void setFlashMode(int mode) {   }
    public int getFlashMode() { return 0; }

    public String getCurrentFlashMode() { return "off"; }

    public String setNextFlashMode() { return getCurrentFlashMode(); }

    public boolean isFlashAvailable() { return false; }

    @Nullable public Bitmap getBitmap() { return null; }

    public int getOrientation() { return 0; }
    public void setOrientation(int rotation) {   }

    public boolean isHdrModeSupported() { return false; }
    public boolean isWideModeSupported() { return false; }

    public boolean isFlooding() { return false; }

    public boolean isSameTakePictureOrientation() { return true; }

    public void rebind() {   }

    public void closeCamera() { destroyCamera(); }

    @Nullable public View getPreviewView() { return null; }

    @Nullable public TextureView getTextureView() { return null; }

    public void initTexture() {   }
    public void showTexture(boolean show, boolean animated) {   }
    public float getTextureHeight(float width, float height) { return 0f; }

    public void setFpsLimit(int fpsLimit) {   }

    @Nullable public CameraSessionWrapper getCameraSession() { return null; }

    @Nullable public Object getCameraSessionObject() { return null; }

    public void setThumbDrawable(@Nullable Drawable drawable) {   }
    public void startSwitchingAnimation() {   }

    public void runHaptic() {   }

    public void setDelegate(@Nullable CameraView.CameraViewDelegate delegate) {   }

    public void setClipTop(int value) {   }
    public void setClipBottom(int value) {   }
     
    public boolean drawInDecoration;
}
