package com.byd.extend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Test;

/** Pure-JVM checks for the persisted direction to AVM preview-slot assignment. */
public final class CameraSourceMapTest {

    @After public void resetRuntimeSeam() {
        CameraSourceMap.install(null);
    }

    @Test public void defaultsMatchTheReferenceVehicle() {
        CameraSourceMap map = CameraSourceMap.defaults();
        assertEquals(4, map.front());
        assertEquals(1, map.rear());
        assertEquals(2, map.left());
        assertEquals(3, map.right());
        assertTrue(map.isDefault());
        assertEquals("front=4 rear=1 left=2 right=3", map.describe());
    }

    @Test public void missingPreferencesReadAsDefaults() {
        CameraRuntimePreferences preferences = new CameraRuntimePreferences();
        CameraSourceMap map = CameraSourceMap.read(preferences);
        assertTrue(map.isDefault());
    }

    @Test public void writtenAssignmentReadsBack() {
        CameraRuntimePreferences preferences = new CameraRuntimePreferences();
        CameraSourceMap value = CameraSourceMap.of(3, 1, 4, 2);
        CameraSourceMap.write(preferences, value);
        assertEquals(value, CameraSourceMap.read(preferences));
        assertEquals("front=3 rear=1 left=4 right=2",
                CameraSourceMap.read(preferences).describe());
    }

    @Test public void resetWritesTheReferenceAssignment() {
        CameraRuntimePreferences preferences = new CameraRuntimePreferences();
        CameraSourceMap.write(preferences, CameraSourceMap.of(1, 2, 3, 4));
        CameraSourceMap.writeDefaults(preferences);
        assertTrue(CameraSourceMap.read(preferences).isDefault());
    }

    @Test public void selectingAnotherDirectionsSlotSwapsThem() {
        CameraSourceMap map = CameraSourceMap.defaults().withIndex(CameraSourceMap.LEFT, 4);
        assertEquals(4, map.left());
        assertEquals(2, map.front());
        assertEquals(1, map.rear());
        assertEquals(3, map.right());
        assertFalse(map.isDefault());
    }

    @Test public void selectingTheOwnSlotIsANoOp() {
        CameraSourceMap map = CameraSourceMap.defaults();
        assertEquals(map, map.withIndex(CameraSourceMap.REAR, 1));
    }

    @Test public void everyPairwiseSwapStaysAPermutation() {
        for (int front = 1; front <= 4; front++) {
            for (int rear = 1; rear <= 4; rear++) {
                for (int left = 1; left <= 4; left++) {
                    for (int right = 1; right <= 4; right++) {
                        if (!distinct(front, rear, left, right)) continue;
                        CameraSourceMap start = CameraSourceMap.of(front, rear, left, right);
                        assertPermutation(start);
                        for (int direction = 0;
                                direction < CameraSourceMap.DIRECTION_COUNT; direction++) {
                            for (int slot = CameraSourceMap.MIN_INDEX;
                                    slot <= CameraSourceMap.MAX_INDEX; slot++) {
                                CameraSourceMap next = start.withIndex(direction, slot);
                                assertEquals(slot, next.index(direction));
                                assertPermutation(next);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean distinct(int a, int b, int c, int d) {
        return a != b && a != c && a != d && b != c && b != d && c != d;
    }

    @Test public void nonPermutationAssignmentsAreRejected() {
        expectInvalid(() -> CameraSourceMap.of(1, 1, 2, 3));
        expectInvalid(() -> CameraSourceMap.of(0, 1, 2, 3));
        expectInvalid(() -> CameraSourceMap.of(5, 1, 2, 3));
        expectInvalid(() -> CameraSourceMap.of(1, 2, 3, 9));
    }

    @Test public void storedNonPermutationIsRepairedToDefaults() {
        CameraRuntimePreferences preferences = new CameraRuntimePreferences();
        preferences.edit()
                .putInt(CameraSourceMap.PREF_FRONT, 1)
                .putInt(CameraSourceMap.PREF_REAR, 1)
                .putInt(CameraSourceMap.PREF_LEFT, 2)
                .putInt(CameraSourceMap.PREF_RIGHT, 3)
                .apply();
        assertTrue(CameraSourceMap.read(preferences).isDefault());
    }

    @Test public void storedOutOfRangeSlotIsRepairedToDefaults() {
        CameraRuntimePreferences preferences = new CameraRuntimePreferences();
        preferences.edit()
                .putInt(CameraSourceMap.PREF_FRONT, 0)
                .putInt(CameraSourceMap.PREF_REAR, 1)
                .putInt(CameraSourceMap.PREF_LEFT, 2)
                .putInt(CameraSourceMap.PREF_RIGHT, 3)
                .apply();
        assertTrue(CameraSourceMap.read(preferences).isDefault());
    }

    @Test public void partialAssignmentIsRepairedToDefaults() {
        CameraRuntimePreferences preferences = new CameraRuntimePreferences();
        preferences.edit().putInt(CameraSourceMap.PREF_FRONT, 1).apply();
        assertTrue(CameraSourceMap.read(preferences).isDefault());
    }

    @Test public void attachmentTranslationFollowsTheAssignment() {
        CameraRuntimePreferences preferences = new CameraRuntimePreferences();
        CameraSourceMap.write(preferences, CameraSourceMap.of(3, 1, 4, 2));
        CameraSourceMap.install(preferences);
        // Logical slots keep the reference meaning: 1 rear, 2 left, 3 right, 4 front.
        assertEquals(1, CameraSourceMap.physicalIndexForAttachment(1));
        assertEquals(4, CameraSourceMap.physicalIndexForAttachment(2));
        assertEquals(2, CameraSourceMap.physicalIndexForAttachment(3));
        assertEquals(3, CameraSourceMap.physicalIndexForAttachment(4));
    }

    @Test public void attachmentTranslationPassesNonDirectionSlotsThrough() {
        CameraSourceMap.install(new CameraRuntimePreferences());
        assertEquals(CameraSourceMap.PANORAMA_INDEX,
                CameraSourceMap.physicalIndexForAttachment(CameraSourceMap.PANORAMA_INDEX));
        assertEquals(4123, CameraSourceMap.physicalIndexForAttachment(4123));
        assertEquals(5, CameraSourceMap.physicalIndexForAttachment(5));
        assertEquals(-1, CameraSourceMap.physicalIndexForAttachment(-1));
    }

    @Test public void attachmentTranslationIsIdentityWithoutInstalledSettings() {
        CameraSourceMap.install(null);
        for (int slot = 1; slot <= 4; slot++) {
            assertEquals(slot, CameraSourceMap.physicalIndexForAttachment(slot));
        }
    }

    @Test public void logicalSlotToDirectionMatchesTheReferenceOrder() {
        assertEquals(CameraSourceMap.REAR, CameraSourceMap.directionForLogicalIndex(1));
        assertEquals(CameraSourceMap.LEFT, CameraSourceMap.directionForLogicalIndex(2));
        assertEquals(CameraSourceMap.RIGHT, CameraSourceMap.directionForLogicalIndex(3));
        assertEquals(CameraSourceMap.FRONT, CameraSourceMap.directionForLogicalIndex(4));
    }

    @Test public void invalidDirectionIsRejected() {
        CameraSourceMap map = CameraSourceMap.defaults();
        expectInvalid(() -> map.index(-1));
        expectInvalid(() -> map.index(CameraSourceMap.DIRECTION_COUNT));
        expectInvalid(() -> map.withIndex(CameraSourceMap.DIRECTION_COUNT, 1));
        expectInvalid(() -> map.withIndex(CameraSourceMap.FRONT, 0));
    }

    @Test public void equalityTracksEveryDirection() {
        CameraSourceMap defaults = CameraSourceMap.defaults();
        assertEquals(defaults, CameraSourceMap.of(4, 1, 2, 3));
        assertNotEquals(defaults, CameraSourceMap.of(3, 1, 2, 4));
        assertEquals(defaults.hashCode(), CameraSourceMap.of(4, 1, 2, 3).hashCode());
    }

    private static void assertPermutation(CameraSourceMap map) {
        boolean[] seen = new boolean[CameraSourceMap.MAX_INDEX + 1];
        for (int direction = 0; direction < CameraSourceMap.DIRECTION_COUNT; direction++) {
            int slot = map.index(direction);
            assertTrue("slot out of range: " + slot, CameraSourceMap.isValidIndex(slot));
            assertFalse("duplicate slot: " + slot, seen[slot]);
            seen[slot] = true;
        }
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected: the assignment must never leave the 1..4 permutation.
        }
    }
}
