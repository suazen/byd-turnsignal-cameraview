package com.byd.extend;

import android.content.SharedPreferences;

/**
 * Persisted vehicle-direction to AVM preview-slot assignment.
 *
 * <p>{@code AVMCamera} exposes five preview slots.  Slot 0 is the stitched panorama
 * (1920x990) and slots 1..4 are the rear, left, right and front fisheye streams
 * (1920x1300) on the reference vehicle.  Other models and firmware revisions can order
 * those four streams differently, so the app must not bake the association into
 * constants: the user marks which slot shows which side.</p>
 *
 * <p>The assignment is always a permutation of slots 1..4.  Two directions claiming one
 * slot would ask the AVM for the same physical stream twice, and the reverse composition
 * already requires its direct sources to be a unique subset of 1..4.  Slot 0 is never
 * assignable because the fan-out derives the source geometry from the logical slot
 * ({@code index == 0 ? 990 : 1300}) and because the panorama is a stitched composite, not
 * a direction.</p>
 */
public final class CameraSourceMap {
    /** Panorama/composite slot; never a direction and never user assignable. */
    public static final int PANORAMA_INDEX = 0;
    public static final int MIN_INDEX = 1;
    public static final int MAX_INDEX = 4;

    /** Stable direction ids shared with the Compose UI and the settings writer. */
    public static final int FRONT = 0;
    public static final int REAR = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 3;
    public static final int DIRECTION_COUNT = 4;

    static final String PREF_FRONT = "camera_source_front";
    static final String PREF_REAR = "camera_source_rear";
    static final String PREF_LEFT = "camera_source_left";
    static final String PREF_RIGHT = "camera_source_right";

    /** Reference-vehicle assignment: front=4, rear=1, left=2, right=3. */
    private static final int[] DEFAULT_INDEXES = {4, 1, 2, 3};
    private static final String[] PREF_KEYS = {
            PREF_FRONT, PREF_REAR, PREF_LEFT, PREF_RIGHT
    };

    /**
     * Live settings snapshot used by the camera helper while it attaches AVM surfaces.
     * The helper installs its replicated preferences once; the replica object is mutated
     * in place when the app pushes a settings change, so no re-install is needed.
     */
    private static volatile SharedPreferences runtimePreferences;

    private final int[] indexes;

    private CameraSourceMap(int[] values) {
        indexes = values.clone();
    }

    // ---------------------------------------------------------------- construction

    public static CameraSourceMap defaults() {
        return new CameraSourceMap(DEFAULT_INDEXES.clone());
    }

    /**
     * Creates a map from explicit slot values.
     *
     * @throws IllegalArgumentException if the four values are not a permutation of 1..4
     */
    public static CameraSourceMap of(int front, int rear, int left, int right) {
        return new CameraSourceMap(requirePermutation(
                new int[]{front, rear, left, right}, "camera source assignment"));
    }

    /**
     * Reads the persisted assignment.  Anything that is not a permutation of 1..4 —
     * including a partially written map, a foreign value or an unrelated key — falls
     * back to the reference assignment instead of failing the camera session.
     */
    public static CameraSourceMap read(SharedPreferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("preferences is null");
        int[] values = new int[DIRECTION_COUNT];
        for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
            values[direction] = readIndex(preferences, PREF_KEYS[direction],
                    DEFAULT_INDEXES[direction]);
        }
        try {
            return new CameraSourceMap(requirePermutation(values, "camera source assignment"));
        } catch (RuntimeException invalidAssignment) {
            return defaults();
        }
    }

    private static int readIndex(
            SharedPreferences preferences, String key, int fallback) {
        try {
            return preferences.getInt(key, fallback);
        } catch (RuntimeException invalidPreference) {
            return fallback;
        }
    }

    // ---------------------------------------------------------------- persistence

    static void write(SharedPreferences preferences, CameraSourceMap value) {
        if (preferences == null || value == null) {
            throw new IllegalArgumentException("camera source assignment arguments required");
        }
        SharedPreferences.Editor editor = preferences.edit();
        for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
            editor.putInt(PREF_KEYS[direction], value.indexes[direction]);
        }
        editor.apply();
    }

    /** Writes the reference assignment, keeping the same keys for a clean reset. */
    static void writeDefaults(SharedPreferences preferences) {
        write(preferences, defaults());
    }

    // ---------------------------------------------------------------- model

    public int index(int direction) {
        requireDirection(direction);
        return indexes[direction];
    }

    public int front() { return indexes[FRONT]; }
    public int rear() { return indexes[REAR]; }
    public int left() { return indexes[LEFT]; }
    public int right() { return indexes[RIGHT]; }

    /**
     * Returns a map where {@code direction} owns {@code index}.  If another direction
     * already owned that slot the two swap, so the result is always a permutation.
     */
    public CameraSourceMap withIndex(int direction, int index) {
        requireDirection(direction);
        if (!isValidIndex(index)) {
            throw new IllegalArgumentException("camera source index out of range: " + index);
        }
        int[] values = indexes.clone();
        if (values[direction] == index) return this;
        int owner = directionOf(values, index);
        if (owner >= 0) values[owner] = values[direction];
        values[direction] = index;
        return new CameraSourceMap(requirePermutation(values, "camera source assignment"));
    }

    public boolean isDefault() {
        for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
            if (indexes[direction] != DEFAULT_INDEXES[direction]) return false;
        }
        return true;
    }

    /** Stable diagnostic form, for example {@code "front=4 rear=1 left=2 right=3"}. */
    public String describe() {
        return "front=" + front() + " rear=" + rear() + " left=" + left() + " right=" + right();
    }

    public static boolean isValidIndex(int index) {
        return index >= MIN_INDEX && index <= MAX_INDEX;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof CameraSourceMap)) return false;
        return java.util.Arrays.equals(indexes, ((CameraSourceMap) other).indexes);
    }

    @Override public int hashCode() {
        return java.util.Arrays.hashCode(indexes);
    }

    @Override public String toString() {
        return "CameraSourceMap{" + describe() + '}';
    }

    // ---------------------------------------------------------------- helper seam

    /** Installs the settings snapshot the helper translates attachment slots through. */
    public static void install(SharedPreferences preferences) {
        runtimePreferences = preferences;
    }

    /**
     * Translates an app-side logical preview slot into the physical AVM slot that
     * actually carries that direction.  Slots outside 1..4 (panorama 0, the vendor
     * detection stream 4123) pass through unchanged.
     */
    public static int physicalIndexForAttachment(int logicalIndex) {
        if (!isValidIndex(logicalIndex)) return logicalIndex;
        SharedPreferences preferences = runtimePreferences;
        if (preferences == null) return logicalIndex;
        return read(preferences).index(directionForLogicalIndex(logicalIndex));
    }

    /**
     * The reference assignment maps logical slot 1 to rear, 2 to left, 3 to right and
     * 4 to front; that is the direction id offset, with slot 4 wrapping to {@link #FRONT}.
     */
    static int directionForLogicalIndex(int logicalIndex) {
        if (logicalIndex == MAX_INDEX) return FRONT;
        requireDirection(logicalIndex);
        return logicalIndex;
    }

    // ---------------------------------------------------------------- validation

    private static int[] requirePermutation(int[] values, String what) {
        if (values == null || values.length != DIRECTION_COUNT) {
            throw new IllegalArgumentException(what + " must assign every direction");
        }
        boolean[] seen = new boolean[MAX_INDEX + 1];
        for (int value : values) {
            if (!isValidIndex(value) || seen[value]) {
                throw new IllegalArgumentException(what + " must be a permutation of 1..4");
            }
            seen[value] = true;
        }
        return values;
    }

    private static void requireDirection(int direction) {
        if (direction < 0 || direction >= DIRECTION_COUNT) {
            throw new IllegalArgumentException("invalid camera direction: " + direction);
        }
    }

    private static int directionOf(int[] values, int index) {
        for (int direction = 0; direction < values.length; direction++) {
            if (values[direction] == index) return direction;
        }
        return -1;
    }
}
