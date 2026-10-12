/**
 * This file is part of LinkiGram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * LinkiGram modifications:
 * Copyright Ettacent, 2026.
 */

package app.nimarkogram.messenger.camera;

import android.annotation.SuppressLint;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Works out which camera modules a logical multi-camera exposes and what zoom ratio
 * reaches each one.
 * <p>
 * A logical multi-camera reports every physical lens through
 * {@link CameraCharacteristics#LENS_INFO_AVAILABLE_FOCAL_LENGTHS}. Switching module is
 * therefore a matter of picking the zoom ratio that lines up with the wanted focal
 * length: the HAL swaps the physical sensor by itself. The wide lens sitting at 1x
 * anchors the scale, so a shorter focal length reads as 0.6x and a longer one as 1.9x.
 */
public final class CameraLensSelector {

    /** One selectable module, described the way camera apps label them. */
    public static final class Lens {
        public final float factor;
        public final float zoomRatio;
        public final float focalLength;
        /** Camera id backing this module, or null when the logical camera handles it. */
        public final String cameraId;

        Lens(float factor, float zoomRatio, float focalLength) {
            this(factor, zoomRatio, focalLength, null);
        }

        Lens(float factor, float zoomRatio, float focalLength, @Nullable String cameraId) {
            this.factor = factor;
            this.zoomRatio = zoomRatio;
            this.focalLength = focalLength;
            this.cameraId = cameraId;
        }
    }

    private static final float MIN_FACTOR = 0.45f;
    private static final float MAX_FACTOR = 3.2f;
    private static final float MIN_FACTOR_GAP = 0.08f;
    private static final float RATIO_EPSILON = 0.02f;

    private CameraLensSelector() {
    }

    /**
     * Lists the modules of a logical camera, widest first, with 1x always present.
     *
     * @return one entry per usable module; a single 1x entry when the camera has no
     * extra lenses, so callers can hide the switcher.
     */
    @SuppressLint("UnsafeOptInUsageError")
    public static List<Lens> lensesFrom(@Nullable CameraCharacteristics characteristics) {
        float[] focals = readFocalLengths(characteristics);
        if (focals.length == 0) {
            return Collections.singletonList(new Lens(1f, 1f, 0f));
        }
        return buildLenses(focals);
    }

    /** True when the camera exposes more than the single 1x lens. */
    public static boolean hasMultipleLenses(List<Lens> lenses) {
        return lenses != null && lenses.size() > 1;
    }

    /** Formats a factor the way the camera UI labels it, e.g. 0.6x / 1x / 1.9x. */
    public static String labelFor(float factor) {
        float value = Math.round(factor * 10f) / 10f;
        if (Math.abs(value - 1f) < 0.05f) {
            return "1x";
        }
        return (value < 1f
                ? String.format(java.util.Locale.US, "%.1f", value)
                : String.format(java.util.Locale.US, "%.1f", value)) + "x";
    }

    /**
     * The entry closest to the given zoom ratio, so a pinch that lands between two
     * modules still resolves to a sensible button state.
     */
    public static Lens closestTo(List<Lens> lenses, float zoomRatio) {
        Lens best = lenses.get(0);
        float bestDistance = Float.MAX_VALUE;
        for (Lens lens : lenses) {
            float distance = Math.abs(lens.zoomRatio - zoomRatio);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = lens;
            }
        }
        return best;
    }

    /** The next module in the list, wrapping around, for a tap-to-cycle button. */
    public static Lens next(List<Lens> lenses, Lens current) {
        if (lenses.isEmpty()) return null;
        int index = current == null ? -1 : lenses.indexOf(current);
        return lenses.get((index + 1) % lenses.size());
    }

    /**
     * Same as {@link #lensesFrom(CameraCharacteristics)} but for callers that only
     * hold the raw focal lengths, such as the CameraX path.
     */
    public static List<Lens> lensesFromFocals(@Nullable float[] focals) {
        if (focals == null || focals.length == 0) {
            return Collections.singletonList(new Lens(1f, 1f, 0f));
        }
        float[] sorted = focals.clone();
        Arrays.sort(sorted);
        return buildLenses(sorted);
    }

    /**
     * Builds the module list by walking every camera the device exposes.
     * <p>
     * Plenty of phones publish each physical lens as its own camera id and report a
     * single focal length for each one, rather than folding them into one logical
     * multi-camera. Reading focal lengths from a single camera therefore finds just
     * one lens and the switcher would never appear, so the ids are enumerated
     * directly and each lens is labelled relative to the widest one in that facing.
     *
     * @param manager       the camera manager to query
     * @param requiredFacing {@link CameraCharacteristics#LENS_FACING_BACK} or FRONT
     * @return modules sorted widest first, or a single 1x entry when the device has
     * only one camera in that direction.
     */
    @SuppressLint("UnsafeOptInUsageError")
    public static List<Lens> lensesFromCameraManager(@Nullable CameraManager manager, int requiredFacing) {
        if (manager == null) {
            return Collections.singletonList(new Lens(1f, 1f, 0f, null));
        }
        ArrayList<Lens> found = new ArrayList<>();
        try {
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics characteristics;
                try {
                    characteristics = manager.getCameraCharacteristics(id);
                } catch (Throwable ignored) {
                    continue;
                }
                if (characteristics == null) continue;
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != requiredFacing) continue;
                float focal = shortestFocal(characteristics);
                if (focal <= 0f) continue;
                found.add(new Lens(0f, 1f, focal, id));
            }
        } catch (Throwable ignored) {
        }

        if (found.size() <= 1) {
            // A single camera in this direction, or a logical camera that reports
            // several focal lengths: fall back to reading them off that one camera.
            String onlyId = found.isEmpty() ? null : found.get(0).cameraId;
            if (onlyId != null) {
                try {
                    return withCameraIds(lensesFrom(manager.getCameraCharacteristics(onlyId)), onlyId);
                } catch (Throwable ignored) {
                }
            }
            return Collections.singletonList(new Lens(1f, 1f, 0f, onlyId));
        }

        // 1x is the middle lens: the shortest focal length is the ultra-wide, which
        // phones label below 1x, and the longest one is the telephoto above it.
        found.sort((a, b) -> Float.compare(a.focalLength, b.focalLength));
        float[] allFocals = new float[found.size()];
        for (int i = 0; i < found.size(); i++) {
            allFocals[i] = found.get(i).focalLength;
        }
        float mainFocal = pickMainFocal(allFocals);
        if (mainFocal <= 0f) mainFocal = found.get(found.size() - 1).focalLength;

        ArrayList<Lens> lenses = new ArrayList<>(found.size());
        for (Lens lens : found) {
            float factor = mainFocal / lens.focalLength;
            if (factor < MIN_FACTOR || factor > MAX_FACTOR) continue;
            lenses.add(new Lens(factor, 1f / factor, lens.focalLength, lens.cameraId));
        }
        if (lenses.isEmpty()) {
            return Collections.singletonList(new Lens(1f, 1f, 0f, found.get(0).cameraId));
        }
        Collections.sort(lenses, (a, b) -> Float.compare(b.factor, a.factor));
        return lenses;
    }

    private static List<Lens> withCameraIds(List<Lens> lenses, @Nullable String cameraId) {
        if (lenses.isEmpty()) return lenses;
        ArrayList<Lens> tagged = new ArrayList<>(lenses.size());
        for (Lens lens : lenses) {
            tagged.add(new Lens(lens.factor, lens.zoomRatio, lens.focalLength, cameraId));
        }
        return tagged;
    }

    @SuppressLint("UnsafeOptInUsageError")
    private static float shortestFocal(@Nullable CameraCharacteristics characteristics) {
        if (characteristics == null) return 0f;
        try {
            float[] focals = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            if (focals == null || focals.length == 0) return 0f;
            float shortest = Float.MAX_VALUE;
            for (float focal : focals) {
                if (focal > 0f && focal < shortest) shortest = focal;
            }
            return shortest == Float.MAX_VALUE ? 0f : shortest;
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    private static List<Lens> buildLenses(float[] sorted) {
        float mainFocal = pickMainFocal(sorted);
        ArrayList<Lens> lenses = new ArrayList<>(sorted.length);
        for (float focal : sorted) {
            if (focal <= 0f) continue;
            float factor = mainFocal / focal;
            if (factor < MIN_FACTOR || factor > MAX_FACTOR) continue;
            if (isDuplicate(lenses, factor)) continue;
            lenses.add(new Lens(factor, 1f / factor, focal));
        }

        boolean hasMain = false;
        for (Lens lens : lenses) {
            if (Math.abs(lens.factor - 1f) <= RATIO_EPSILON) {
                hasMain = true;
                break;
            }
        }
        if (!hasMain) {
            lenses.add(new Lens(1f, 1f, mainFocal));
        }

        Collections.sort(lenses, (a, b) -> Float.compare(b.factor, a.factor));
        return lenses;
    }

    @SuppressLint("UnsafeOptInUsageError")
    private static float[] readFocalLengths(@Nullable CameraCharacteristics characteristics) {
        if (characteristics == null) return new float[0];
        try {
            float[] focals = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            if (focals == null || focals.length == 0) return new float[0];
            float[] sorted = focals.clone();
            Arrays.sort(sorted);
            return sorted;
        } catch (Throwable ignored) {
            return new float[0];
        }
    }

    /**
     * Picks the focal length that represents 1x.
     * <p>
     * The list mixes ultra-wide, wide and telephoto lenses, so the 1x lens is the
     * one closest to the middle of the range rather than simply the shortest or the
     * longest.
     */
    private static float pickMainFocal(float[] sorted) {
        if (sorted.length == 0) return 0f;
        if (sorted.length == 1) return sorted[0];
        if (sorted.length == 2) return sorted[0];
        return sorted[sorted.length / 2];
    }

    private static boolean isDuplicate(List<Lens> lenses, float factor) {
        for (Lens lens : lenses) {
            if (Math.abs(lens.factor - factor) < MIN_FACTOR_GAP) return true;
        }
        return false;
    }
}