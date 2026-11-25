package com.pacefit.ai.domain

import org.junit.Assert.*
import org.junit.Test

class LocalFeaturesTest {
    private fun detect(speed: Double, stepsPerSample: Long?, activity: ActivityHint = ActivityHint.UNKNOWN): DetectedSegment {
        val detector = TransportDetector(15)
        var segment: DetectedSegment? = null
        repeat(4) { index ->
            segment = detector.add(MovementReading(
                1_000L + index * 5_000, index * 5_000L, speed, stepsPerSample?.times(index), activity,
            ))
        }
        return requireNotNull(segment)
    }

    @Test fun acceptsSpanishCoordinatesAndBoundaries() {
        assertEquals(Coordinates(-16.5, -68.15), LocalInput.coordinates("-16,5", "-68,15"))
        assertEquals(Coordinates(-90.0, 180.0), LocalInput.coordinates("-90", "180"))
    }

    @Test fun rejectsMissingOutOfRangeAndNonfiniteCoordinates() {
        listOf("", "NaN", "Infinity", "91", "-91").forEach { assertNull(LocalInput.coordinates(it, "0")) }
        assertNull(LocalInput.coordinates("0", "181"))
        assertNull(LocalInput.coordinates("0", "-Infinity"))
    }

    @Test fun windowHasExplicitBounds() {
        assertEquals(15, LocalInput.windowSeconds("15"))
        assertEquals(120, LocalInput.windowSeconds("120"))
        listOf("14", "121", "NaN", "30.5", "").forEach { assertNull(LocalInput.windowSeconds(it)) }
    }

    @Test fun quantityDeterminesPoints() {
        assertEquals(listOf(5, 10, 20), WasteQuantity.entries.map { it.points })
    }

    @Test fun runningAboveSevenKmhIsNotVehicle() {
        val result = detect(12.0, 14)
        assertEquals(TransportClass.ACTIVE, result.classification)
        assertTrue(result.includedInMetrics)
        assertEquals(50.0, result.distanceMeters, 0.001)
        assertEquals(42L, result.steps)
    }

    @Test fun sustainedHighSpeedWithoutStepsIsExcluded() {
        val result = detect(36.0, 0)
        assertEquals(TransportClass.VEHICLE, result.classification)
        assertFalse(result.includedInMetrics)
        assertEquals(150.0, result.distanceMeters, 0.001)
    }

    @Test fun noStepSensorDoesNotBecomeProofOfVehicle() {
        val result = detect(36.0, null)
        assertEquals(TransportClass.UNKNOWN, result.classification)
        assertFalse(result.includedInMetrics)
        assertNull(result.steps)
    }

    @Test fun explicitRunningAllowsMissingSensor() {
        assertEquals(TransportClass.ACTIVE, detect(10.0, null, ActivityHint.RUN).classification)
    }

    @Test fun explicitVehicleSupportsSlowTransport() {
        assertEquals(TransportClass.VEHICLE, detect(6.0, null, ActivityHint.VEHICLE).classification)
    }

    @Test fun standingStillIsNotExercise() {
        assertEquals(TransportClass.UNKNOWN, detect(0.0, 0).classification)
    }

    @Test fun gapsAndInvalidSpeedsResetWindow() {
        val detector = TransportDetector(15)
        fun reading(time: Long, speed: Double = 20.0) = MovementReading(time, time, speed, 0, ActivityHint.UNKNOWN)
        assertNull(detector.add(reading(0)))
        assertNull(detector.add(reading(5_000)))
        assertNull(detector.add(reading(10_000, Double.NaN)))
        assertNull(detector.add(reading(15_000)))
        assertNull(detector.add(reading(40_000)))
        assertNull(detector.add(reading(45_000)))
        assertNull(detector.add(reading(50_000)))
        assertNotNull(detector.add(reading(55_000)))
    }

    @Test fun changingActivityStartsANewWindow() {
        val detector = TransportDetector(15)
        repeat(3) { index ->
            assertNull(detector.add(MovementReading(index * 5_000L, index * 5_000L, 20.0, 0, ActivityHint.UNKNOWN)))
        }
        assertNull(detector.add(MovementReading(15_000, 15_000, 20.0, 0, ActivityHint.RUN)))
    }

    @Test fun counterResetDoesNotLookLikeNegativeSteps() {
        val detector = TransportDetector(15)
        var result: DetectedSegment? = null
        listOf(100L, 110L, 0L, 10L).forEachIndexed { index, steps ->
            result = detector.add(MovementReading(index * 5_000L, index * 5_000L, 20.0, steps, ActivityHint.UNKNOWN))
        }
        assertNull(result!!.steps)
        assertEquals(TransportClass.UNKNOWN, result!!.classification)
    }
}
